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
 * if a source with that name does not exist yet. Existing sources (and their
 * checkpoints) are never overwritten; change them through the harvest API.
 * A source without a {@code urlTemplate} is always stored disabled.
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
			if (sourceRepository.findByName(s.getName()) != null) {
				continue;
			}
			if (s.getType() == HarvestSource.Type.HTML_LISTING && s.getUrlTemplate() == null) {
				s.setEnabled(false);
				log.info("Harvest source '{}' added disabled: set its urlTemplate via PUT /api/kg/harvest/sources/{{id}}",
						s.getName());
			}
			sourceRepository.save(s);
		}
	}
}
