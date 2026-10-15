package com.strikready.sandbox.services.kg.harvest;

import java.util.ArrayList;
import java.util.List;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import org.springframework.stereotype.Component;

/**
 * A fixed list of official document URLs (e.g. a batch prepared by hand or
 * exported from a court's index), queued once.
 */
@Component
public class UrlListConnector implements SourceConnector {

	@Override
	public boolean supports(HarvestSource.Type type) {
		return type == HarvestSource.Type.URL_LIST;
	}

	@Override
	public List<DiscoveredDocument> discover(HarvestSource source, HarvestWindow window) {
		List<DiscoveredDocument> out = new ArrayList<>();
		for (String url : source.getUrls()) {
			if (url != null && !url.trim().isEmpty()) {
				out.add(new DiscoveredDocument(url.trim(), null));
			}
		}
		return out;
	}
}
