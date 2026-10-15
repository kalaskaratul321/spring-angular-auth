package com.strikready.sandbox.services.kg.importer;

import java.io.IOException;

/**
 * A non-200 HTTP response from an official source. 4xx responses other than
 * 408/429 are permanent: retrying the same URL will not help.
 */
public class HttpStatusException extends IOException {

	private static final long serialVersionUID = 1L;

	private final int status;

	public HttpStatusException(int status, String url) {
		super("HTTP " + status + " from " + url);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

	public boolean isRetryable() {
		return status >= 500 || status == 408 || status == 429;
	}
}
