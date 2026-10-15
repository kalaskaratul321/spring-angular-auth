package com.strikready.sandbox.services.kg.harvest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.services.kg.importer.FetchedDocument;
import com.strikready.sandbox.services.kg.importer.HttpStatusException;
import com.strikready.sandbox.services.kg.importer.OfficialSourceFetcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads date-paged HTML listing pages on an official site (e.g. a court's
 * "judgments uploaded between" page) and returns the document links on them.
 * Listing pages go through {@link OfficialSourceFetcher}, so the same host
 * allow-list, redirect checks and per-host throttling apply. Links to hosts
 * outside the allow-list are ignored.
 */
@Component
public class HtmlListingConnector implements SourceConnector {

	private final OfficialSourceFetcher fetcher;

	@Autowired
	public HtmlListingConnector(OfficialSourceFetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public boolean supports(HarvestSource.Type type) {
		return type == HarvestSource.Type.HTML_LISTING;
	}

	@Override
	public List<DiscoveredDocument> discover(HarvestSource source, HarvestWindow window) throws IOException {
		if (source.getUrlTemplate() == null) {
			throw new IllegalArgumentException("Source " + source.getName() + " has no urlTemplate");
		}
		boolean paged = source.getUrlTemplate().contains("{page}");
		int lastPage = paged ? source.getFirstPage() + Math.max(1, source.getMaxPages()) - 1 : source.getFirstPage();

		Map<String, DiscoveredDocument> found = new LinkedHashMap<>();
		for (int page = source.getFirstPage(); page <= lastPage; page++) {
			String listingUrl = expand(source, window, page);
			FetchedDocument listing;
			try {
				listing = fetcher.fetch(listingUrl);
			} catch (HttpStatusException e) {
				// Many sites answer 404 for a page past the end of the results.
				if (paged && page > source.getFirstPage() && e.getStatus() == 404) {
					break;
				}
				throw e;
			}
			Document html = Jsoup.parse(new String(listing.getContent(), StandardCharsets.UTF_8),
					listing.getResolvedUrl());
			int before = found.size();
			collectLinks(html, source, fetcher, found);
			if (found.size() == before) {
				break; // empty page, or the site ignores {page} and repeats itself
			}
		}
		return new ArrayList<>(found.values());
	}

	/**
	 * Adds the document links on {@code page} matching the source's
	 * {@code linkSelector}/{@code linkPattern} on allowed hosts, keyed by
	 * document identity so the same judgment is listed once.
	 */
	static void collectLinks(Document page, HarvestSource source, OfficialSourceFetcher fetcher,
			Map<String, DiscoveredDocument> found) {
		Pattern linkPattern = Pattern.compile(source.getLinkPattern() == null ? ".*" : source.getLinkPattern());
		String selector = source.getLinkSelector() == null ? "a[href]" : source.getLinkSelector();
		for (Element a : page.select(selector)) {
			String href = a.absUrl("href");
			if (href.isEmpty() || !linkPattern.matcher(href).find() || !allowed(href, fetcher)) {
				continue;
			}
			String key = UrlKeys.identity(href);
			if (!found.containsKey(key)) {
				found.put(key, new DiscoveredDocument(href, titleHint(a)));
			}
		}
	}

	/** Link text, or the text of its table row when the link itself is just "View"/"PDF". */
	private static String titleHint(Element a) {
		String text = a.text().trim();
		if (text.length() > 8 && !text.matches("(?i)view|download|pdf|open|click here")) {
			return text;
		}
		Element row = a.closest("tr");
		String rowText = row == null ? text : row.text().trim();
		if (rowText.isEmpty()) {
			return null;
		}
		return rowText.length() > 300 ? rowText.substring(0, 300) : rowText;
	}

	static String expand(HarvestSource source, HarvestWindow window, int page) {
		String url = source.getUrlTemplate().replace("{page}", String.valueOf(page));
		if (window.isDated()) {
			DateTimeFormatter f = DateTimeFormatter.ofPattern(source.getDateFormat());
			url = url.replace("{from}", f.format(window.getStart())).replace("{to}", f.format(window.getEnd()));
		}
		return url;
	}

	private static boolean allowed(String href, OfficialSourceFetcher fetcher) {
		try {
			URL u = new URL(href);
			return ("https".equals(u.getProtocol()) || "http".equals(u.getProtocol()))
					&& fetcher.isAllowedHost(u.getHost());
		} catch (MalformedURLException e) {
			return false;
		}
	}
}
