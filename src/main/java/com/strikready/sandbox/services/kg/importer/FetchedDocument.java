package com.strikready.sandbox.services.kg.importer;

import java.time.Instant;

/**
 * A document downloaded into memory. It is never written to disk or the
 * database; callers extract what they need and let it be garbage collected.
 */
public class FetchedDocument {

	private final String requestedUrl;
	private final String resolvedUrl;
	private final String contentType;
	private final byte[] content;
	private final String sha256;
	private final String lastModified;
	private final Instant fetchedAt;

	public FetchedDocument(String requestedUrl, String resolvedUrl, String contentType, byte[] content, String sha256,
			String lastModified, Instant fetchedAt) {
		this.requestedUrl = requestedUrl;
		this.resolvedUrl = resolvedUrl;
		this.contentType = contentType;
		this.content = content;
		this.sha256 = sha256;
		this.lastModified = lastModified;
		this.fetchedAt = fetchedAt;
	}

	public String getRequestedUrl() {
		return requestedUrl;
	}

	public String getResolvedUrl() {
		return resolvedUrl;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getContent() {
		return content;
	}

	public String getSha256() {
		return sha256;
	}

	public String getLastModified() {
		return lastModified;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}
}
