package com.strikready.sandbox.services.kg.harvest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.services.kg.importer.OfficialSourceFetcherTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Simulates a PHP court search: session cookie, a CSRF token that changes on
 * every form load, table-layout "From Date"/"To Date" inputs with opaque
 * names, a default-selected bench, a named submit button, "too many results"
 * for ranges over 3 days, and a session-bound "Next" page.
 */
public class SearchFormConnectorTest {

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private HttpServer server;
	private String base;
	private final Map<String, String> tokenBySession = new HashMap<>();
	private final List<Map<String, String>> submissions = new ArrayList<>();

	@Before
	public void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ord_qry.php", ex -> {
			String sid = session(ex);
			String token = UUID.randomUUID().toString();
			tokenBySession.put(sid, token);
			respond(ex, "<html><body>"
					+ "<form id='search' action='ord_qry_action.php' method='post'>"
					+ "<input type='hidden' name='_token' value='" + token + "'>"
					+ "<table><tr><td>Bench</td><td><select name='m_bench'><option value='B'>Mumbai</option>"
					+ "<option value='N' selected>Nagpur</option></select></td></tr>"
					+ "<tr><td>From Date</td><td><input type='text' name='m_frm' class='datepick'></td></tr>"
					+ "<tr><td>To Date</td><td><input type='text' name='m_to2' class='datepick'></td></tr>"
					+ "<tr><td>Judgment</td><td><input type='checkbox' name='onlyjudg' value='Y' checked>"
					+ "<input type='checkbox' name='reportable' value='Y'></td></tr>"
					+ "</table><input type='submit' name='submit1' value='Search'>"
					+ "<input type='reset' name='rst' value='Clear'></form>"
					+ "<form action='/other'><input name='q'></form></body></html>");
		});
		server.createContext("/ord_qry_action.php", ex -> {
			String sid = session(ex);
			Map<String, String> form = parseForm(ex);
			if (form.containsKey("page")) {
				// Page 2 only works inside the session that ran the search.
				respond(ex, tokenBySession.containsKey(sid)
						? "<a href='/files/" + form.get("k") + "-1.pdf'>View</a><a href='/files/" + form.get("k") + "-3.pdf'>View</a>"
						: "<p>Session expired</p>");
				return;
			}
			submissions.add(form);
			if (!form.get("_token").equals(tokenBySession.get(sid))) {
				respond(ex, "<p>Session expired</p>");
				return;
			}
			LocalDate from = LocalDate.parse(form.get("m_frm"), DMY);
			LocalDate to = LocalDate.parse(form.get("m_to2"), DMY);
			if (to.toEpochDay() - from.toEpochDay() + 1 > 3) {
				respond(ex, "<p>Your search returned too many results - please refine your search</p>");
				return;
			}
			String key = from + "_" + to;
			respond(ex, "<table><tr><td>WP/1/2024 X v. Y</td><td><a href='/files/" + key + "-1.pdf'>View</a></td></tr>"
					+ "<tr><td>WP/2/2024</td><td><a href='/files/" + key + "-2.pdf'>View</a></td></tr></table>"
					+ "<a href='/ord_qry_action.php?page=2&k=" + key + "'>Next</a>");
		});
		server.createContext("/captcha.php", ex -> respond(ex, "<form method='post' action='/x'>"
				+ "<input name='fromdate' placeholder='From Date'><input name='todate' placeholder='To Date'>"
				+ "<img src='/securimage/captcha_show.php'><input name='captcha_code'></form>"));
		server.start();
		base = "http://localhost:" + server.getAddress().getPort();
	}

	@After
	public void stop() {
		server.stop(0);
	}

	private HarvestSource source(String formPage) {
		HarvestSource s = new HarvestSource();
		s.setName("bhc-test");
		s.setType(HarvestSource.Type.SEARCH_FORM);
		s.setFormPageUrl(base + formPage);
		s.setDateFormat("dd-MM-yyyy");
		s.setLinkSelector("a[href]");
		s.setLinkPattern("\\.pdf$");
		s.setMaxPages(5);
		return s;
	}

	private SearchFormConnector connector() {
		return new SearchFormConnector(OfficialSourceFetcherTestSupport.local(Collections.singletonList("localhost")));
	}

	@Test
	public void submitsLikeABrowserSplitsWindowsAndFollowsNext() throws IOException {
		HarvestSource s = source("/ord_qry.php");
		s.getExtraFields().put("reportable", "Y");
		List<DiscoveredDocument> docs = connector().discover(s,
				new HarvestWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)));

		// 7 days -> too many -> 1..3 ok, 4..7 too many -> 4..5, 6..7
		List<String> ranges = new ArrayList<>();
		for (Map<String, String> f : submissions) {
			ranges.add(f.get("m_frm") + ".." + f.get("m_to2"));
		}
		assertEquals(Collections.unmodifiableList(java.util.Arrays.asList("01-01-2024..07-01-2024",
				"01-01-2024..03-01-2024", "04-01-2024..07-01-2024", "04-01-2024..05-01-2024",
				"06-01-2024..07-01-2024")), ranges);

		// Each of the 3 windows: 2 links on page 1, 1 new link on page 2 (page 1's link repeated is not duplicated)
		assertEquals(9, docs.size());
		assertTrue(docs.get(2).getUrl().endsWith("/files/2024-01-01_2024-01-03-3.pdf"));
		assertTrue(docs.get(0).getUrl().endsWith("/files/2024-01-01_2024-01-03-1.pdf"));
		assertEquals("WP/1/2024 X v. Y View", docs.get(0).getTitleHint());

		Map<String, String> first = submissions.get(1);
		assertEquals("N", first.get("m_bench"));        // selected option kept
		assertEquals("Y", first.get("onlyjudg"));        // checked box sent
		assertEquals("Y", first.get("reportable"));      // extraFields override
		assertEquals("Search", first.get("submit1"));    // submit button sent
		assertTrue(!first.containsKey("rst"));           // reset never sent
		assertTrue(!first.containsKey("q"));             // other form ignored
	}

	@Test
	public void diagnoseReportsDetectedFields() throws IOException {
		Map<String, Object> d = connector().diagnose(source("/ord_qry.php"));
		assertEquals("m_frm", d.get("fromField"));
		assertEquals("m_to2", d.get("toField"));
		assertEquals("POST", d.get("method"));
		assertEquals(base + "/ord_qry_action.php", d.get("action"));
		assertEquals(false, d.get("captcha"));
	}

	@Test
	public void refusesCaptchaForms() throws IOException {
		try {
			connector().discover(source("/captcha.php"),
					new HarvestWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)));
			fail("captcha form must be refused");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("captcha"));
		}
	}

	@Test
	public void singleDayWithTooManyResultsFails() {
		HarvestSource s = source("/ord_qry.php");
		s.setTooManyResultsPattern("(?i)WP/1/2024");
		try {
			connector().discover(s, new HarvestWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)));
			fail("expected failure");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("single day"));
		}
	}

	// ------------------------------------------------------------ server helpers

	private static String session(HttpExchange ex) {
		String cookie = ex.getRequestHeaders().getFirst("Cookie");
		if (cookie != null && cookie.startsWith("PHPSESSID=")) {
			return cookie.substring("PHPSESSID=".length());
		}
		String sid = UUID.randomUUID().toString();
		ex.getResponseHeaders().add("Set-Cookie", "PHPSESSID=" + sid + "; path=/; HttpOnly");
		return sid;
	}

	private static Map<String, String> parseForm(HttpExchange ex) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		String body;
		try (InputStream in = ex.getRequestBody(); Scanner sc = new Scanner(in, "UTF-8")) {
			body = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
		}
		if ("GET".equals(ex.getRequestMethod())) {
			body = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
		}
		for (String pair : body.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				out.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
			}
		}
		return out;
	}

	private static void respond(HttpExchange ex, String html) throws IOException {
		byte[] b = ("<html><body>" + html + "</body></html>").getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		ex.sendResponseHeaders(200, b.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(b);
		}
	}
}
