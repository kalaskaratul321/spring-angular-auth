package com.strikready.sandbox.services.kg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.bson.Document;
import org.junit.Test;

public class GroupKeyTest {

	@Test
	public void readsKeysFlattenedBySpringData() {
		Document flattened = new Document("category", "CRIMINAL").append("courtLevel", "HIGH_COURT").append("count", 1);
		assertEquals("CRIMINAL", KnowledgeGraphService.groupKey(flattened, "category"));
		assertEquals("HIGH_COURT", KnowledgeGraphService.groupKey(flattened, "courtLevel"));
	}

	@Test
	public void readsKeysNestedUnderId() {
		Document nested = new Document("_id", new Document("category", "RERA").append("courtLevel", "TRIBUNAL"))
				.append("count", 1);
		assertEquals("RERA", KnowledgeGraphService.groupKey(nested, "category"));
		assertEquals("TRIBUNAL", KnowledgeGraphService.groupKey(nested, "courtLevel"));
	}

	@Test
	public void missingKeyIsNull() {
		assertNull(KnowledgeGraphService.groupKey(new Document("count", 3), "courtLevel"));
	}
}
