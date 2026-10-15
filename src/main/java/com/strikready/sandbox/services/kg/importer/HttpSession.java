package com.strikready.sandbox.services.kg.importer;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal per-host cookie jar for one harvesting conversation with a site
 * (load search form, submit, follow result pages). Kept in memory only.
 */
public class HttpSession {

	private final Map<String, Map<String, String>> cookiesByHost = new LinkedHashMap<>();

	public synchronized void store(String host, List<String> setCookieHeaders) {
		if (setCookieHeaders == null) {
			return;
		}
		Map<String, String> jar = cookiesByHost.computeIfAbsent(host.toLowerCase(Locale.ROOT),
				h -> new LinkedHashMap<>());
		for (String header : setCookieHeaders) {
			try {
				for (HttpCookie c : HttpCookie.parse(header)) {
					if (c.getMaxAge() == 0) {
						jar.remove(c.getName());
					} else {
						jar.put(c.getName(), c.getValue());
					}
				}
			} catch (IllegalArgumentException ignored) {
				// malformed cookie: skip it
			}
		}
	}

	/** Cookie header value for a host, or null. */
	public synchronized String header(String host) {
		Map<String, String> jar = cookiesByHost.get(host.toLowerCase(Locale.ROOT));
		if (jar == null || jar.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : jar.entrySet()) {
			if (sb.length() > 0) {
				sb.append("; ");
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		return sb.toString();
	}
}
