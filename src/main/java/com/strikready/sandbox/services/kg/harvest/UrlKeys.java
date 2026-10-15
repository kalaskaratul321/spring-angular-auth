package com.strikready.sandbox.services.kg.harvest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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

	/**
	 * What identifies the document behind a URL: the normalised URL without
	 * scheme, or a site-specific identity where the URL carries volatile parts.
	 */
	public static String identity(String url) {
		String bhc = bombayHighCourtFile(url);
		return bhc != null ? "bombayhighcourt:" + bhc : normalise(url).replaceFirst("^https?://", "//");
	}

	/**
	 * Bombay High Court serves judgments as
	 * {@code generatenewauth.php?bhcpar=<base64>}, where the base64 decodes to
	 * {@code path=./writereaddata/data/judgements/2007/&fname=OSSJ111703.pdf&...&spassphrase=<timestamp>&...}.
	 * The passphrase changes between listings, so the file is identified by
	 * path + fname only. Returns null for other URLs.
	 */
	static String bombayHighCourtFile(String url) {
		try {
			URI u = new URI(url.trim());
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
			if (!host.endsWith("bombayhighcourt.nic.in") && !host.endsWith("bombayhighcourt.gov.in")) {
				return null;
			}
			String param = queryParam(u.getRawQuery(), "bhcpar");
			if (param == null) {
				return null;
			}
			String decoded = new String(Base64.getMimeDecoder().decode(param), StandardCharsets.UTF_8);
			String path = queryParam(decoded, "path");
			String fname = queryParam(decoded, "fname");
			if (fname == null || fname.isEmpty()) {
				return null;
			}
			String dir = path == null ? "" : path.replaceFirst("^\\./", "").replaceAll("/+$", "");
			return (dir.isEmpty() ? "" : dir + "/") + fname;
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		}
	}

	private static String queryParam(String query, String name) {
		if (query == null) {
			return null;
		}
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0 && pair.substring(0, eq).equals(name)) {
				try {
					return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
				} catch (UnsupportedEncodingException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		return null;
	}

	/**
	 * Dedup key: SHA-1 of {@link #identity(String)}. Two links to the same
	 * document get the same key even if the site embeds per-request tokens.
	 */
	public static String dedupKey(String url) {
		String n = identity(url);
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
