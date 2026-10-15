package com.strikready.sandbox.services.kg.harvest;

import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Adds the predefined harvest sources from {@code kg/harvest-sources.json}
 * if a source with that name does not exist yet. A seeded source is replaced
 * by a newer {@code seedVersion} only while it has never run, so sources in
 * use (checkpoints, edits made through the API) are never overwritten.
 * Seeded sources are always stored disabled: enable them after a preview.
 */
@Component
public class HarvestSourceSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(HarvestSourceSeeder.class);
	private static final String SEED_FILE = "kg/harvest-sources.json";

	@Autowired
	private HarvestSourceRepository sourceRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public void run(String... args) throws Exception {
		List<HarvestSource> sources;
		try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
			sources = objectMapper.readValue(in, new TypeReference<List<HarvestSource>>() {
			});
		}
		for (HarvestSource s : sources) {
			HarvestSource existing = sourceRepository.findByName(s.getName());
			if (existing != null) {
				if (existing.getSeedVersion() >= s.getSeedVersion() || existing.getLastRunAt() != null) {
					continue;
				}
				s.setId(existing.getId());
			}
			s.setEnabled(false);
			sourceRepository.save(s);
			log.info("Harvest source '{}' (seed v{}) stored disabled; preview it with "
					+ "POST /api/kg/harvest/sources/{}/preview?from=..&to=.., then /resume", s.getName(),
					s.getSeedVersion(), s.getId());
		}
	}
}
