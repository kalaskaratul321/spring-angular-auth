package com.strikready.sandbox.services.kg.importer;

import com.strikready.sandbox.models.kg.LegalCase;

/**
 * Import one judgment from an official URL. Anything set in {@code metadata}
 * (title, courtCode, category, issues with topics, appealFromCaseId, ...)
 * overrides what is extracted from the document.
 */
public class ImportRequest {

	private String url;
	private LegalCase metadata;

	public ImportRequest() {
	}

	public ImportRequest(String url, LegalCase metadata) {
		this.url = url;
		this.metadata = metadata;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public LegalCase getMetadata() {
		return metadata;
	}

	public void setMetadata(LegalCase metadata) {
		this.metadata = metadata;
	}
}
