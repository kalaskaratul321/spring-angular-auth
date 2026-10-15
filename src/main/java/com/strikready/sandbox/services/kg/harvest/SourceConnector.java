package com.strikready.sandbox.services.kg.harvest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;

/**
 * Finds document URLs on one kind of official source for a date window.
 * Implementations must be idempotent: discovering a window twice is normal
 * (overlap, retries after failover) and is de-duplicated by the queue.
 */
public interface SourceConnector {

	boolean supports(HarvestSource.Type type);

	List<DiscoveredDocument> discover(HarvestSource source, HarvestWindow window) throws IOException;

	/** What the connector sees on the live site (for the preview endpoint); null if nothing to report. */
	default Map<String, Object> diagnose(HarvestSource source) throws IOException {
		return null;
	}
}
