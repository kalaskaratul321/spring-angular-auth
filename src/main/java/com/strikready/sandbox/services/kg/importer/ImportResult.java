package com.strikready.sandbox.services.kg.importer;

import java.util.ArrayList;
import java.util.List;

import com.strikready.sandbox.models.kg.LegalCase;

/**
 * Outcome of importing one URL.
 */
public class ImportResult {

	public enum Status {
		CREATED, UPDATED, UNCHANGED, FAILED
	}

	private String url;
	private Status status;
	private LegalCase legalCase;
	/** Fields filled from the document (as opposed to supplied metadata). */
	private List<String> extractedFields = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();
	private String error;

	public static ImportResult failed(String url, String error) {
		ImportResult r = new ImportResult();
		r.url = url;
		r.status = Status.FAILED;
		r.error = error;
		return r;
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
