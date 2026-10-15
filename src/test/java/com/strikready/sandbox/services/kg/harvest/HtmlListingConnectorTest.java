package com.strikready.sandbox.services.kg.harvest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.services.kg.importer.OfficialSourceFetcherTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HtmlListingConnectorTest {

	private HttpServer server;
	private String base;
	private final List<String> requested = new ArrayList<>();

	@Before
	public void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/list", ex -> {
			String q = ex.getRequestURI().getQuery();
			requested.add(q);
			String body;
			if (q.contains("page=1")) {
				body = "<a href='/files/a.pdf'>Criminal Appeal 1/2020</a><a href='files/b.pdf?v=1'>B</a>"
						+ "<a href='https://example.com/c.pdf'>offsite</a><a href='/about.html'>About</a>";
			} else if (q.contains("page=2")) {
				body = "<a href='/files/a.pdf'>dup</a><a href='/files/d.PDF'>D</a>";
			} else {
				body = "<p>No records found</p>";
			}
			byte[] b = ("<html><body>" + body + "</body></html>").getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
			ex.sendResponseHeaders(200, b.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(b);
			}
		});
		server.start();
		base = "http://localhost:" + server.getAddress().getPort();
	}

	@After
	public void stop() {
		server.stop(0);
	}

	@Test
	public void collectsAllowedPdfLinksAcrossPagesUntilEmpty() throws IOException {
		HarvestSource s = new HarvestSource();
		s.setName("test");
		s.setUrlTemplate(base + "/list?from={from}&to={to}&page={page}");
		s.setDateFormat("dd-MM-yyyy");
		HarvestWindow w = new HarvestWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

		HtmlListingConnector connector = new HtmlListingConnector(
				OfficialSourceFetcherTestSupport.local(Collections.singletonList("localhost")));
		List<DiscoveredDocument> docs = connector.discover(s, w);

		List<String> urls = new ArrayList<>();
		for (DiscoveredDocument d : docs) {
			urls.add(d.getUrl());
		}
		assertEquals(urls.toString(), 3, docs.size());
		assertTrue(urls.contains(base + "/files/a.pdf"));
		assertTrue(urls.contains(base + "/files/b.pdf?v=1"));
		assertTrue(urls.contains(base + "/files/d.PDF"));
		assertEquals("Criminal Appeal 1/2020", docs.get(0).getTitleHint());
		assertEquals(3, requested.size());
		assertTrue(requested.get(0).contains("from=01-01-2024&to=07-01-2024"));
	}
}
