package com.strikready.sandbox.models.kg;

import java.time.Instant;

/**
 * Where a case's data came from. Only this metadata is kept: the file itself
 * is read in memory during import and discarded, so the official URL is the
 * single source of truth for the judgment text.
 */
public class SourceDocument {

	/** URL as requested, on an allowed official host. */
	private String url;
	/** URL actually served after redirects (also on an allowed host). */
	private String resolvedUrl;
	private String host;
	private String contentType;
	private long contentLength;
	/** SHA-256 of the fetched bytes, to detect when the official copy changes. */
	private String sha256;
	private Instant fetchedAt;
	private String lastModified;
	/** Title from the PDF metadata or the HTML title tag. */
	private String documentTitle;
	private int pageCount;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getResolvedUrl() {
		return resolvedUrl;
	}

	public void setResolvedUrl(String resolvedUrl) {
		this.resolvedUrl = resolvedUrl;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void setContentLength(long contentLength) {
		this.contentLength = contentLength;
	}

	public String getSha256() {
		return sha256;
	}

	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public void setFetchedAt(Instant fetchedAt) {
		this.fetchedAt = fetchedAt;
	}

	public String getLastModified() {
		return lastModified;
	}

	public void setLastModified(String lastModified) {
		this.lastModified = lastModified;
	}

	public String getDocumentTitle() {
		return documentTitle;
	}

	public void setDocumentTitle(String documentTitle) {
		this.documentTitle = documentTitle;
	}

	public int getPageCount() {
		return pageCount;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}
}
