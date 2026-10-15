package com.strikready.sandbox.services.kg;

import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikready.sandbox.models.kg.Court;
import com.strikready.sandbox.repositories.kg.CourtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the Maharashtra court hierarchy (High Court benches down to
 * Sessions/Magistrate courts, plus RERA, revenue, family and consumer forums)
 * from {@code kg/maharashtra-courts.json}. Existing courts are left untouched,
 * so codes added to the file are picked up on the next start.
 */
@Component
public class CourtHierarchySeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(CourtHierarchySeeder.class);
	private static final String SEED_FILE = "kg/maharashtra-courts.json";

	@Autowired
	private CourtRepository courtRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public void run(String... args) throws Exception {
		List<Court> courts;
		try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
			courts = objectMapper.readValue(in, new TypeReference<List<Court>>() {
			});
		}
		int added = 0;
		for (Court court : courts) {
			if (courtRepository.findByCode(court.getCode()) == null) {
				courtRepository.save(court);
				added++;
			}
		}
		log.info("Court hierarchy seed: {} courts in file, {} added", courts.size(), added);
	}
}
