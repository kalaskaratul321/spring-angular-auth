package com.strikready.sandbox.services.kg.importer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Downloads judgments and reference files from official government websites
 * only. The allow-list is checked on the requested URL and again on every
 * redirect, hosts resolving to private/loopback addresses are refused, and the
 * body is read into memory up to a size cap. Nothing is written to disk.
 */
@Component
public class OfficialSourceFetcher {

	private static final int MAX_REDIRECTS = 5;
	private static final String USER_AGENT = "MahaLegalAI-KnowledgeGraph/1.0";

	private final List<String> allowedHosts;
	private final boolean allowHttp;
	private final long maxBytes;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;
	private final boolean blockPrivateAddresses;
	private final long perHostDelayMs;
	/** Earliest time the next request to each host may start. */
	private final Map<String, Long> nextSlotByHost = new HashMap<>();

	@Autowired
	public OfficialSourceFetcher(@Value("${kg.import.allowed-hosts:gov.in,nic.in}") List<String> allowedHosts,
			@Value("${kg.import.allow-http:false}") boolean allowHttp,
			@Value("${kg.import.max-bytes:26214400}") long maxBytes,
			@Value("${kg.import.connect-timeout-ms:15000}") int connectTimeoutMs,
			@Value("${kg.import.read-timeout-ms:60000}") int readTimeoutMs,
			@Value("${kg.import.per-host-delay-ms:1000}") long perHostDelayMs) {
		this(allowedHosts, allowHttp, maxBytes, connectTimeoutMs, readTimeoutMs, true, perHostDelayMs);
	}

	/** Visible for tests, which serve documents from a loopback server. */
	OfficialSourceFetcher(List<String> allowedHosts, boolean allowHttp, long maxBytes, int connectTimeoutMs,
			int readTimeoutMs, boolean blockPrivateAddresses) {
		this(allowedHosts, allowHttp, maxBytes, connectTimeoutMs, readTimeoutMs, blockPrivateAddresses, 0);
	}

	OfficialSourceFetcher(List<String> allowedHosts, boolean allowHttp, long maxBytes, int connectTimeoutMs,
			int readTimeoutMs, boolean blockPrivateAddresses, long perHostDelayMs) {
		this.perHostDelayMs = perHostDelayMs;
		List<String> hosts = new ArrayList<>();
		for (String h : allowedHosts) {
			String t = h.trim().toLowerCase(Locale.ROOT);
			if (!t.isEmpty()) {
				hosts.add(t.startsWith(".") ? t.substring(1) : t);
			}
		}
		this.allowedHosts = Collections.unmodifiableList(hosts);
		this.allowHttp = allowHttp;
		this.maxBytes = maxBytes;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
		this.blockPrivateAddresses = blockPrivateAddresses;
	}

	public List<String> getAllowedHosts() {
		return allowedHosts;
	}

	public FetchedDocument fetch(String url) throws IOException {
		URL current = validate(url);
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			throttle(current.getHost().toLowerCase(Locale.ROOT));
			HttpURLConnection conn = (HttpURLConnection) current.openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setConnectTimeout(connectTimeoutMs);
			conn.setReadTimeout(readTimeoutMs);
			conn.setRequestProperty("User-Agent", USER_AGENT);
			conn.setRequestProperty("Accept", "application/pdf, text/html;q=0.9, text/plain;q=0.8");
			try {
				int status = conn.getResponseCode();
				if (status >= 300 && status < 400) {
					String location = conn.getHeaderField("Location");
					if (location == null) {
						throw new IOException("Redirect without Location from " + current);
					}
					current = validate(new URL(current, location).toString());
					continue;
				}
				if (status != HttpURLConnection.HTTP_OK) {
					throw new HttpStatusException(status, current.toString());
				}
				long declared = conn.getContentLengthLong();
				if (declared > maxBytes) {
					throw new IOException("Document is " + declared + " bytes, limit is " + maxBytes);
				}
				byte[] body;
				try (InputStream in = conn.getInputStream()) {
					body = readCapped(in);
				}
				return new FetchedDocument(url, current.toString(), conn.getContentType(), body, sha256(body),
						conn.getHeaderField("Last-Modified"), Instant.now());
			} finally {
				conn.disconnect();
			}
		}
		throw new IOException("Too many redirects for " + url);
	}

	/** Throws IllegalArgumentException unless the URL is on an allowed official host. */
	public URL validate(String url) {
		URL u;
		try {
			u = new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid URL: " + url);
		}
		String protocol = u.getProtocol().toLowerCase(Locale.ROOT);
		if (!"https".equals(protocol) && !("http".equals(protocol) && allowHttp)) {
			throw new IllegalArgumentException("Only https URLs are accepted: " + url);
		}
		if (u.getUserInfo() != null) {
			throw new IllegalArgumentException("URLs with credentials are not accepted");
		}
		String host = u.getHost().toLowerCase(Locale.ROOT);
		if (!isAllowedHost(host)) {
			throw new IllegalArgumentException(
					"Host " + host + " is not an allowed official source (allowed: " + allowedHosts + ")");
		}
		if (blockPrivateAddresses) {
			checkPublicAddress(host);
		}
		return u;
	}

	public boolean isAllowedHost(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		if (h.endsWith(".")) {
			h = h.substring(0, h.length() - 1);
		}
		for (String allowed : allowedHosts) {
			if (h.equals(allowed) || h.endsWith("." + allowed)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Spaces requests to the same host at least {@code perHostDelayMs} apart
	 * (within this instance), so bulk harvesting does not overload court servers.
	 */
	private void throttle(String host) throws IOException {
		if (perHostDelayMs <= 0) {
			return;
		}
		long wait;
		synchronized (nextSlotByHost) {
			long now = System.currentTimeMillis();
			Long slot = nextSlotByHost.get(host);
			long start = slot == null || slot < now ? now : slot;
			nextSlotByHost.put(host, start + perHostDelayMs);
			wait = start - now;
		}
		if (wait > 0) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while waiting to fetch from " + host);
			}
		}
	}

	private static void checkPublicAddress(String host) {
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		} catch (UnknownHostException e) {
			// Resolved by the outbound proxy, if one is configured.
			return;
		}
		for (InetAddress a : addresses) {
			if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
					|| a.isMulticastAddress()) {
				throw new IllegalArgumentException("Host " + host + " resolves to a non-public address");
			}
		}
	}

	private byte[] readCapped(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[16 * 1024];
		long total = 0;
		int n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			if (total > maxBytes) {
				throw new IOException("Document exceeds limit of " + maxBytes + " bytes");
			}
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	static String sha256(byte[] data) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
