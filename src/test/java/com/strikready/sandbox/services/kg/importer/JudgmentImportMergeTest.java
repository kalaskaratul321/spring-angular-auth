package com.strikready.sandbox.services.kg.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.LegalIssue;
import com.strikready.sandbox.models.kg.VerdictResult;
import org.junit.Test;

public class JudgmentImportMergeTest {

	@Test
	public void suppliedFieldsWinAndParsedFieldsFillTheRest() {
		LegalCase parsed = new JudgmentParser().parse(JudgmentParserTest.HC_JUDGMENT);

		LegalCase supplied = new LegalCase();
		supplied.setTitle("Kulkarni v. A. Traders");
		supplied.setIssues(Collections.singletonList(
				new LegalIssue("Cheque dishonour - sentencing", "Is fine alone adequate?", "Yes")));

		List<String> extracted = new ArrayList<>();
		LegalCase merged = JudgmentImportService.merge(parsed, supplied, extracted);

		assertEquals("Kulkarni v. A. Traders", merged.getTitle());
		assertEquals("Cheque dishonour - sentencing", merged.getIssues().get(0).getTopic());
		assertEquals("BHC-MUM", merged.getCourtCode());
		assertEquals(VerdictResult.PARTLY_ALLOWED, merged.getVerdict().getResult());
		assertEquals(3, merged.getReferences().size());

		assertFalse(extracted.contains("title"));
		assertFalse(extracted.contains("issues"));
		assertTrue(extracted.contains("courtCode"));
		assertTrue(extracted.contains("verdict"));
		assertTrue(extracted.contains("references"));
	}
}
