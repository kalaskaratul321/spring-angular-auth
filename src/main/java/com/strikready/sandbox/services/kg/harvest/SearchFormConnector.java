package com.strikready.sandbox.services.kg.harvest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.services.kg.importer.FetchedDocument;
import com.strikready.sandbox.services.kg.importer.HttpSession;
import com.strikready.sandbox.services.kg.importer.OfficialSourceFetcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Harvests a court's judgment search form (e.g. the Bombay High Court unified
 * search) by submitting it once per date window, like a browser would:
 *
 * <ol>
 * <li>loads {@code formPageUrl} with a cookie session and picks the form
 * ({@code formSelector}, or the first form with from/to date fields);</li>
 * <li>finds the date inputs by name, id, placeholder or label text ("From
 * Date" / "To Date") unless {@code fromField}/{@code toField} are given;</li>
 * <li>sends every field the browser would (hidden CSRF tokens, selected
 * options, checked boxes, the submit button), overridden by
 * {@code extraFields}, with the window's dates;</li>
 * <li>collects judgment links from the results and follows "next" links;</li>
 * <li>if the site says there are too many results, splits the window in half
 * (down to a single day).</li>
 * </ol>
 *
 * The form page is reloaded before every submission so single-use tokens stay
 * valid. Forms protected by a captcha are refused with a clear error: they
 * cannot be harvested automatically. All requests go through
 * {@link OfficialSourceFetcher} (allow-list, redirect checks, throttling).
 */
@Component
public class SearchFormConnector implements SourceConnector {

	private static final Pattern CAPTCHA = Pattern.compile("(?i)captcha|securimage|vercode|security[_ -]?code|verification[_ -]?code");
	private static final Pattern DATEISH = Pattern.compile("(?i)date|dt\\b|\\bdt|datepicker|calendar");
	private static final Pattern FROM = Pattern.compile("(?i)from|frm|fdate|fdt|start|date1");
	private static final Pattern TO = Pattern.compile("(?i)\\bto\\b|todate|to_date|to-date|todt|tdate|tdt|upto|up to|till|\\bend|date2");
	private static final Pattern CHARSET = Pattern.compile("(?i)charset=([\\w-]+)");

	private final OfficialSourceFetcher fetcher;

	@Autowired
	public SearchFormConnector(OfficialSourceFetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public boolean supports(HarvestSource.Type type) {
		return type == HarvestSource.Type.SEARCH_FORM;
	}

	@Override
	public List<DiscoveredDocument> discover(HarvestSource source, HarvestWindow window) throws IOException {
		if (!window.isDated()) {
			throw new IllegalArgumentException("Search form sources need a dated window");
		}
		HttpSession session = new HttpSession();
		Map<String, DiscoveredDocument> found = new LinkedHashMap<>();
		discoverWindow(source, window.getStart(), window.getEnd(), session, found);
		return new ArrayList<>(found.values());
	}

	@Override
	public Map<String, Object> diagnose(HarvestSource source) throws IOException {
		FormSpec spec = loadForm(source, new HttpSession());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("formPageUrl", source.getFormPageUrl());
		out.put("action", spec.action);
		out.put("method", spec.method);
		out.put("fromField", spec.fromField);
		out.put("toField", spec.toField);
		out.put("dateInputType", spec.isoDates ? "date (yyyy-MM-dd)" : source.getDateFormat());
		Map<String, String> fields = new LinkedHashMap<>();
		for (String[] f : spec.fields) {
			fields.put(f[0], f[1]);
		}
		out.put("fieldsSent", fields);
		out.put("captcha", spec.captcha);
		return out;
	}

	private void discoverWindow(HarvestSource source, LocalDate from, LocalDate to, HttpSession session,
			Map<String, DiscoveredDocument> found) throws IOException {
		FormSpec spec = loadForm(source, session);
		if (spec.captcha) {
			throw new IllegalStateException("The search form at " + source.getFormPageUrl()
					+ " has a captcha; it cannot be harvested automatically");
		}
		DateTimeFormatter fmt = spec.isoDates ? DateTimeFormatter.ISO_LOCAL_DATE
				: DateTimeFormatter.ofPattern(source.getDateFormat());
		List<String[]> fields = new ArrayList<>();
		for (String[] f : spec.fields) {
			if (!f[0].equals(spec.fromField) && !f[0].equals(spec.toField)
					&& !source.getExtraFields().containsKey(f[0])) {
				fields.add(f);
			}
		}
		for (Map.Entry<String, String> e : source.getExtraFields().entrySet()) {
			fields.add(new String[] { e.getKey(), e.getValue() });
		}
		fields.add(new String[] { spec.fromField, fmt.format(from) });
		fields.add(new String[] { spec.toField, fmt.format(to) });

		FetchedDocument results = fetcher.submitForm(spec.action, spec.method, fields, session);
		Document page = parse(results);

		Pattern tooMany = source.getTooManyResultsPattern() == null ? null
				: Pattern.compile(source.getTooManyResultsPattern());
		if (tooMany != null && tooMany.matcher(page.text()).find()) {
			long days = ChronoUnit.DAYS.between(from, to) + 1;
			if (days <= 1) {
				throw new IOException("Too many results for a single day (" + from
						+ "); narrow the search with extraFields, e.g. a bench or case type");
			}
			LocalDate mid = from.plusDays(days / 2 - 1);
			discoverWindow(source, from, mid, session, found);
			discoverWindow(source, mid.plusDays(1), to, session, found);
			return;
		}

		Set<String> visited = new HashSet<>();
		visited.add(results.getResolvedUrl());
		int pages = 1;
		while (true) {
			int before = found.size();
			HtmlListingConnector.collectLinks(page, source, fetcher, found);
			if (pages >= Math.max(1, source.getMaxPages()) || found.size() == before && pages > 1) {
				break;
			}
			String next = nextPage(page, source);
			if (next == null || !visited.add(next)) {
				break;
			}
			page = parse(fetcher.fetch(next, session));
			pages++;
		}
	}

	private static String nextPage(Document page, HarvestSource source) {
		if (source.getNextPageSelector() == null || source.getNextPageSelector().trim().isEmpty()) {
			return null;
		}
		for (Element a : page.select(source.getNextPageSelector())) {
			String href = a.absUrl("href");
			if (!href.isEmpty() && href.startsWith("http")) {
				return href;
			}
		}
		return null;
	}

	// ------------------------------------------------------------ the form

	static final class FormSpec {
		String action;
		String method;
		String fromField;
		String toField;
		boolean isoDates;
		boolean captcha;
		final List<String[]> fields = new ArrayList<>();
	}

	private FormSpec loadForm(HarvestSource source, HttpSession session) throws IOException {
		if (source.getFormPageUrl() == null) {
			throw new IllegalArgumentException("Source " + source.getName() + " has no formPageUrl");
		}
		Document page = parse(fetcher.fetch(source.getFormPageUrl(), session));
		return describeForm(page, source);
	}

	/** Visible for tests. */
	static FormSpec describeForm(Document page, HarvestSource source) {
		List<Element> forms = new ArrayList<>();
		if (source.getFormSelector() != null && !source.getFormSelector().trim().isEmpty()) {
			forms.addAll(page.select(source.getFormSelector()));
		} else {
			forms.addAll(page.select("form"));
		}
		for (Element form : forms) {
			FormSpec spec = new FormSpec();
			Element from = source.getFromField() != null ? named(form, source.getFromField()) : detect(form, true);
			Element to = source.getToField() != null ? named(form, source.getToField()) : detect(form, false);
			if (from == null || to == null || from == to) {
				continue;
			}
			spec.fromField = from.attr("name");
			spec.toField = to.attr("name");
			spec.isoDates = "date".equalsIgnoreCase(from.attr("type"));
			String action = form.absUrl("action");
			spec.action = action.isEmpty() ? page.location() : action;
			spec.method = form.attr("method").isEmpty() ? "GET" : form.attr("method").toUpperCase(Locale.ROOT);
			spec.captcha = hasCaptcha(form);
			collectFields(form, spec.fields);
			return spec;
		}
		throw new IllegalArgumentException("No search form with From/To date fields found on "
				+ page.location() + (source.getFormSelector() == null ? "" : " (formSelector " + source.getFormSelector() + ")")
				+ "; set formSelector, fromField and toField");
	}

	private static Element named(Element form, String name) {
		return form.selectFirst("[name=" + name + "]");
	}

	/** Finds the from (or to) date input by its name, id, placeholder, class and label text. */
	private static Element detect(Element form, boolean wantFrom) {
		for (Element input : form.select("input")) {
			String type = input.attr("type").toLowerCase(Locale.ROOT);
			if (input.attr("name").isEmpty() || type.matches("submit|button|image|reset|file|checkbox|radio")) {
				continue;
			}
			String hint = hint(form, input);
			boolean dateish = "date".equals(type) || DATEISH.matcher(hint).find();
			if (!dateish) {
				continue;
			}
			boolean isFrom = FROM.matcher(hint).find();
			boolean isTo = TO.matcher(hint).find();
			if (wantFrom && isFrom && !isTo || !wantFrom && isTo && !isFrom) {
				return input;
			}
		}
		return null;
	}

	private static String hint(Element form, Element input) {
		StringBuilder sb = new StringBuilder();
		sb.append(input.attr("name")).append(' ').append(input.attr("id")).append(' ')
				.append(input.attr("placeholder")).append(' ').append(input.attr("class")).append(' ')
				.append(input.attr("title")).append(' ').append(input.attr("aria-label"));
		if (!input.id().isEmpty()) {
			Element label = form.ownerDocument() == null ? null
					: form.ownerDocument().selectFirst("label[for=" + input.id() + "]");
			if (label != null) {
				sb.append(' ').append(label.text());
			}
		}
		Element parentLabel = input.closest("label");
		if (parentLabel != null) {
			sb.append(' ').append(parentLabel.ownText());
		}
		// Table layouts: <td>From Date</td><td><input ...></td>
		Element cell = input.closest("td, th");
		if (cell != null && cell.previousElementSibling() != null) {
			sb.append(' ').append(cell.previousElementSibling().text());
		}
		// Text directly before the input in the same container
		Element prev = input.previousElementSibling();
		if (prev != null && !"input".equals(prev.tagName())) {
			sb.append(' ').append(prev.text());
		}
		return sb.toString().replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
	}

	private static boolean hasCaptcha(Element form) {
		for (Element el : form.select("input, img, iframe, div")) {
			String attrs = el.attr("name") + " " + el.id() + " " + el.attr("class") + " " + el.attr("src");
			if (CAPTCHA.matcher(attrs).find()) {
				return true;
			}
		}
		return form.select(".g-recaptcha, .h-captcha, [data-sitekey]").size() > 0;
	}

	/** The name/value pairs a browser submits for this form (except the submit button choice). */
	private static void collectFields(Element form, List<String[]> fields) {
		boolean submitAdded = false;
		for (Element el : form.select("input, select, textarea, button")) {
			String name = el.attr("name");
			if (name.isEmpty() || el.hasAttr("disabled")) {
				continue;
			}
			String tag = el.tagName();
			String type = el.attr("type").toLowerCase(Locale.ROOT);
			if ("select".equals(tag)) {
				List<Element> selected = el.select("option[selected]");
				if (selected.isEmpty() && el.selectFirst("option") != null) {
					selected.add(el.selectFirst("option"));
				}
				for (Element o : el.hasAttr("multiple") ? selected : selected.subList(0, Math.min(1, selected.size()))) {
					fields.add(new String[] { name, o.hasAttr("value") ? o.attr("value") : o.text() });
				}
			} else if ("textarea".equals(tag)) {
				fields.add(new String[] { name, el.text() });
			} else if ("button".equals(tag) || type.matches("submit|image")) {
				if (!submitAdded && ("button".equals(tag) ? !type.matches("button|reset") : true)) {
					fields.add(new String[] { name, el.attr("value") });
					submitAdded = true;
				}
			} else if (type.matches("checkbox|radio")) {
				if (el.hasAttr("checked")) {
					fields.add(new String[] { name, el.hasAttr("value") ? el.attr("value") : "on" });
				}
			} else if (!type.matches("reset|file|button")) {
				fields.add(new String[] { name, el.attr("value") });
			}
		}
	}

	private static Document parse(FetchedDocument doc) {
		Charset cs = StandardCharsets.UTF_8;
		if (doc.getContentType() != null) {
			Matcher m = CHARSET.matcher(doc.getContentType());
			if (m.find()) {
				try {
					cs = Charset.forName(m.group(1));
				} catch (RuntimeException ignored) {
					// keep UTF-8
				}
			}
		}
		return Jsoup.parse(new String(doc.getContent(), cs), doc.getResolvedUrl());
	}
}
