package com.strikready.sandbox.services.kg.importer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OfficialSourceFetcherTest {

	private final OfficialSourceFetcher production = new OfficialSourceFetcher(Arrays.asList("gov.in", "nic.in"),
			false, 1024, 1000, 1000);

	private HttpServer server;
	private String base;

	@Before
	public void startServer() throws IOException {
		byte[] pdf = samplePdf("IN THE HIGH COURT OF JUDICATURE AT BOMBAY");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/judgment.pdf", ex -> {
			byte[] body = pdf;
			ex.getResponseHeaders().add("Content-Type", "application/pdf");
			ex.sendResponseHeaders(200, body.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(body);
			}
		});
		server.createContext("/redirect-out", ex -> {
			ex.getResponseHeaders().add("Location", "https://example.com/evil.pdf");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/redirect-in", ex -> {
			ex.getResponseHeaders().add("Location", "/judgment.pdf");
			ex.sendResponseHeaders(302, -1);
			ex.close();
		});
		server.createContext("/big", ex -> {
			byte[] body = new byte[200_000];
			ex.sendResponseHeaders(200, 0);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(body);
			}
		});
		server.start();
		base = "http://localhost:" + server.getAddress().getPort();
	}

	@After
	public void stopServer() {
		server.stop(0);
	}

	private OfficialSourceFetcher local(long maxBytes) {
		return new OfficialSourceFetcher(Collections.singletonList("localhost"), true, maxBytes, 2000, 2000, false);
	}

	@Test
	public void allowsOnlyOfficialHosts() {
		assertTrue(production.isAllowedHost("bombayhighcourt.nic.in"));
		assertTrue(production.isAllowedHost("judgments.ecourts.gov.in"));
		assertTrue(production.isAllowedHost("maharera.maharashtra.gov.in"));
		assertTrue(production.isAllowedHost("www.indiacode.nic.in."));
		assertFalse(production.isAllowedHost("example.com"));
		assertFalse(production.isAllowedHost("gov.in.example.com"));
		assertFalse(production.isAllowedHost("fakegov.in"));
		assertFalse(production.isAllowedHost("nic.in-judgments.com"));
	}

	@Test
	public void rejectsHttpCredentialsAndOtherHosts() {
		expectRejected("http://bombayhighcourt.nic.in/a.pdf");
		expectRejected("https://user:pw@bombayhighcourt.nic.in/a.pdf");
		expectRejected("https://example.com/a.pdf");
		expectRejected("file:///etc/passwd");
		expectRejected("not a url");
	}

	@Test
	public void fetchesIntoMemoryWithHash() throws IOException {
		FetchedDocument doc = local(1_000_000).fetch(base + "/judgment.pdf");
		assertEquals("application/pdf", doc.getContentType());
		assertEquals(64, doc.getSha256().length());
		assertEquals(OfficialSourceFetcher.sha256(doc.getContent()), doc.getSha256());

		DocumentTextExtractor.ExtractedText text = new DocumentTextExtractor().extract(doc);
		assertTrue(text.getText().contains("HIGH COURT OF JUDICATURE AT BOMBAY"));
		assertEquals(1, text.getPageCount());
	}

	@Test
	public void followsRedirectsOnlyWithinAllowedHosts() throws IOException {
		FetchedDocument doc = local(1_000_000).fetch(base + "/redirect-in");
		assertEquals(base + "/judgment.pdf", doc.getResolvedUrl());
		assertEquals(base + "/redirect-in", doc.getRequestedUrl());
		try {
			local(1_000_000).fetch(base + "/redirect-out");
			fail("redirect to a non-official host must be refused");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("example.com"));
		}
	}

	@Test
	public void enforcesSizeLimit() {
		try {
			local(1000).fetch(base + "/big");
			fail("oversized document must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("limit"));
		}
	}

	@Test
	public void extractsHtml() throws IOException {
		byte[] html = ("<html><head><title>Order</title><script>x()</script></head><body>"
				+ "<p>CRIMINAL APPEAL NO. 1 OF 2020</p><p>The appeal is dismissed.</p></body></html>")
				.getBytes(StandardCharsets.UTF_8);
		FetchedDocument doc = new FetchedDocument("u", "u", "text/html; charset=UTF-8", html, "h", null, null);
		DocumentTextExtractor.ExtractedText text = new DocumentTextExtractor().extract(doc);
		assertEquals("Order", text.getTitle());
		assertTrue(text.getText().contains("CRIMINAL APPEAL NO. 1 OF 2020\n"));
		assertFalse(text.getText().contains("x()"));
	}

	private void expectRejected(String url) {
		try {
			production.validate(url);
			fail("should reject " + url);
		} catch (IllegalArgumentException expected) {
			// ok
		}
	}

	static byte[] samplePdf(String... lines) throws IOException {
		try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			pdf.addPage(page);
			try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
				cs.beginText();
				cs.setFont(PDType1Font.HELVETICA, 11);
				cs.setLeading(14);
				cs.newLineAtOffset(50, 740);
				for (String l : lines) {
					cs.showText(l);
					cs.newLine();
				}
				cs.endText();
			}
			pdf.save(out);
			return out.toByteArray();
		}
	}

	@Test
	public void samplePdfRoundTrips() throws IOException {
		byte[] a = samplePdf("x");
		assertArrayEquals(Arrays.copyOf(a, 4), "%PDF".getBytes(StandardCharsets.US_ASCII));
	}
}
