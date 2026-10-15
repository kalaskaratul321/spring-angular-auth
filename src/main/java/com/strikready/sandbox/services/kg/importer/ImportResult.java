package com.strikready.sandbox.services.kg.importer;

import java.util.ArrayList;
import java.util.List;

import com.strikready.sandbox.models.kg.LegalCase;

/**
 * Outcome of importing one URL.
 */
public class ImportResult {

	public enum Status {
		CREATED, UPDATED, UNCHANGED,
		/** Same file (by SHA-256) already imported from another URL; nothing saved. */
		DUPLICATE, FAILED
	}

	private String url;
	private Status status;
	private LegalCase legalCase;
	/** Fields filled from the document (as opposed to supplied metadata). */
	private List<String> extractedFields = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();
	private String error;
	/** For FAILED: whether retrying later might succeed (network/5xx vs 404/not allowed). */
	private boolean retryable;

	public static ImportResult failed(String url, String error, boolean retryable) {
		ImportResult r = new ImportResult();
		r.url = url;
		r.status = Status.FAILED;
		r.error = error;
		r.retryable = retryable;
		return r;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public LegalCase getLegalCase() {
		return legalCase;
	}

	public void setLegalCase(LegalCase legalCase) {
		this.legalCase = legalCase;
	}

	public List<String> getExtractedFields() {
		return extractedFields;
	}

	public List<String> getWarnings() {
		return warnings;
	}

	public String getError() {
		return error;
	}
}
