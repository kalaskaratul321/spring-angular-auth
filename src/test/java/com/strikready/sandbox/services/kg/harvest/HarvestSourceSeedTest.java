package com.strikready.sandbox.services.kg.harvest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

public class HarvestSourceSeedTest {

	@Test
	public void bombayHighCourtSeedLoadsDisabled() throws Exception {
		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
		List<HarvestSource> sources;
		try (InputStream in = new ClassPathResource("kg/harvest-sources.json").getInputStream()) {
			sources = mapper.readValue(in, new TypeReference<List<HarvestSource>>() {
			});
		}
		HarvestSource bhc = sources.get(0);
		assertEquals("bombay-high-court-judgments", bhc.getName());
		assertFalse(bhc.isEnabled());
		assertNull(bhc.getUrlTemplate());
		assertEquals(LocalDate.of(2000, 1, 1), bhc.getStartDate());
		assertEquals("generatenewauth\\.php\\?bhcpar=", bhc.getLinkPattern());
	}
}
