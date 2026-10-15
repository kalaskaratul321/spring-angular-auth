package com.strikready.sandbox.services.kg.harvest;

/**
 * A document link found on an official listing.
 */
public class DiscoveredDocument {

	private final String url;
	private final String titleHint;

	public DiscoveredDocument(String url, String titleHint) {
		this.url = url;
		this.titleHint = titleHint;
	}

	public String getUrl() {
		return url;
	}

	public String getTitleHint() {
		return titleHint;
	}
}
