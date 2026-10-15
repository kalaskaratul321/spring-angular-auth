package com.strikready.sandbox.services.kg.importer;

import java.util.List;

/** Builds a fetcher that may talk to a loopback test server. */
public final class OfficialSourceFetcherTestSupport {

	private OfficialSourceFetcherTestSupport() {
	}

	public static OfficialSourceFetcher local(List<String> hosts) {
		return new OfficialSourceFetcher(hosts, true, 1_000_000, 2000, 2000, false);
	}
}
