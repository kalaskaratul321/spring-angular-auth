package com.strikready.sandbox.services.kg.harvest;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Normalises document URLs so the same file reached through cosmetically
 * different links (case of the host, default port, fragment, "./" segments)
 * yields one dedup key.
 */
public final class UrlKeys {

	private UrlKeys() {
	}

	public static String normalise(String url) {
		try {
			URI u = new URI(url.trim()).normalize();
			String scheme = u.getScheme() == null ? "https" : u.getScheme().toLowerCase(Locale.ROOT);
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
			if (host.endsWith(".")) {
				host = host.substring(0, host.length() - 1);
			}
			int port = u.getPort();
			boolean defaultPort = port == -1 || ("https".equals(scheme) && port == 443)
					|| ("http".equals(scheme) && port == 80);
			String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
			StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
			if (!defaultPort) {
				sb.append(':').append(port);
			}
			sb.append(path);
			if (u.getRawQuery() != null) {
				sb.append('?').append(u.getRawQuery());
			}
			return sb.toString();
		} catch (URISyntaxException e) {
			return url.trim();
		}
	}

	/** Dedup key: SHA-1 of the normalised URL, with the scheme ignored (http/https serve the same file). */
	public static String dedupKey(String url) {
		String n = normalise(url).replaceFirst("^https?://", "//");
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(n.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
