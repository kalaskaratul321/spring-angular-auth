package com.strikready.sandbox.services.kg.importer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Turns a fetched PDF or HTML document into plain text, entirely in memory.
 */
@Component
public class DocumentTextExtractor {

	private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

	public ExtractedText extract(FetchedDocument doc) throws IOException {
		byte[] bytes = doc.getContent();
		String type = doc.getContentType() == null ? "" : doc.getContentType().toLowerCase(Locale.ROOT);
		if (isPdf(bytes) || type.contains("application/pdf")) {
			return extractPdf(bytes);
		}
		String raw = new String(bytes, charset(type));
		if (type.contains("html") || raw.trim().toLowerCase(Locale.ROOT).startsWith("<!doctype html")
				|| raw.trim().toLowerCase(Locale.ROOT).startsWith("<html")) {
			return extractHtml(raw);
		}
		if (type.isEmpty() || type.startsWith("text/")) {
			return new ExtractedText(raw, null, 0);
		}
		throw new IOException("Unsupported content type: " + doc.getContentType());
	}

	ExtractedText extractPdf(byte[] bytes) throws IOException {
		try (PDDocument pdf = PDDocument.load(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			String title = pdf.getDocumentInformation() == null ? null : pdf.getDocumentInformation().getTitle();
			return new ExtractedText(stripper.getText(pdf), title, pdf.getNumberOfPages());
		}
	}

	ExtractedText extractHtml(String html) {
		Document doc = Jsoup.parse(html);
		doc.select("script, style, nav, header, footer").remove();
		doc.select("br").after("\n");
		doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6, table, pre").before("\n");
		String text = doc.body() == null ? "" : doc.body().wholeText();
		return new ExtractedText(text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n"),
				doc.title(), 0);
	}

	private static boolean isPdf(byte[] b) {
		return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
	}

	private static Charset charset(String contentType) {
		Matcher m = CHARSET.matcher(contentType);
		if (m.find()) {
			try {
				return Charset.forName(m.group(1));
			} catch (RuntimeException ignored) {
				// fall through to UTF-8
			}
		}
		return StandardCharsets.UTF_8;
	}

	public static class ExtractedText {
		private final String text;
		private final String title;
		private final int pageCount;

		public ExtractedText(String text, String title, int pageCount) {
			this.text = text == null ? "" : text;
			this.title = title;
			this.pageCount = pageCount;
		}

		public String getText() {
			return text;
		}

		public String getTitle() {
			return title;
		}

		public int getPageCount() {
			return pageCount;
		}
	}
}
