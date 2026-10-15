package com.strikready.sandbox.services.kg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.Classification;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.LegalIssue;
import com.strikready.sandbox.models.kg.NodeType;
import com.strikready.sandbox.models.kg.StatuteReference;
import org.junit.Test;

public class CaseCategoryClassifierTest {

	private final CaseCategoryClassifier classifier = new CaseCategoryClassifier();

	private static LegalCase legalCase(String caseType, String title, StatuteReference... statutes) {
		LegalCase c = new LegalCase();
		c.setCaseType(caseType);
		c.setTitle(title);
		c.setStatutes(Arrays.asList(statutes));
		return c;
	}

	@Test
	public void sessionsMurderTrialIsCriminal() {
		Classification c = classifier.classify(
				legalCase("Sessions Case", "State of Maharashtra v. Ramesh - murder trial",
						new StatuteReference("IPC", "302"), new StatuteReference("Cr.P.C.", "313")),
				Arrays.asList(CaseCategory.CIVIL, CaseCategory.CRIMINAL));
		assertEquals(CaseCategory.CRIMINAL, c.getCategory());
		assertTrue(c.getConfidence() > 0.8);
	}

	@Test
	public void bailSectionGivesBailSubCategory() {
		Classification c = classifier.classify(
				legalCase("Criminal Bail Application", "Applicant seeks regular bail",
						new StatuteReference("CrPC", "439")),
				null);
		assertEquals(CaseCategory.CRIMINAL, c.getCategory());
		assertEquals("Bail", c.getSubCategory());
	}

	@Test
	public void maintenanceUnderCrpc125IsFamilyNotCriminal() {
		Classification c = classifier.classify(
				legalCase("Criminal Revision Application", "Wife's claim for maintenance",
						new StatuteReference("Cr.P.C.", "125")),
				null);
		assertEquals(CaseCategory.FAMILY, c.getCategory());
		assertEquals("Maintenance", c.getSubCategory());
	}

	@Test
	public void maharashtraLandRevenueCodeIsLand() {
		Classification c = classifier.classify(legalCase("RTS Appeal", "Challenge to mutation entry in 7/12 extract",
				new StatuteReference("Maharashtra Land Revenue Code, 1966", "150")), null);
		assertEquals(CaseCategory.LAND, c.getCategory());
		assertEquals("Land Revenue", c.getSubCategory());
	}

	@Test
	public void delayedPossessionComplaintIsRera() {
		Classification c = classifier.classify(
				legalCase("Complaint", "Allottee v. Promoter for delayed possession and interest",
						new StatuteReference("RERA", "18")),
				Collections.singletonList(CaseCategory.RERA));
		assertEquals(CaseCategory.RERA, c.getCategory());
	}

	@Test
	public void specificPerformanceSuitIsCivil() {
		Classification c = classifier.classify(legalCase("Special Civil Suit", "Suit for specific performance",
				new StatuteReference("Specific Relief Act, 1963", "10")), null);
		assertEquals(CaseCategory.CIVIL, c.getCategory());
		assertEquals("Specific Performance", c.getSubCategory());
	}

	@Test
	public void chequeBounceIsCriminal() {
		Classification c = classifier.classify(legalCase("Summary Criminal Case", "Dishonour of cheque",
				new StatuteReference("Negotiable Instruments Act, 1881", "138")), null);
		assertEquals(CaseCategory.CRIMINAL, c.getCategory());
		assertEquals("Cheque Dishonour", c.getSubCategory());
	}

	@Test
	public void nothingKnownIsOther() {
		Classification c = classifier.classify(legalCase("Misc", "In re X"), null);
		assertEquals(CaseCategory.OTHER, c.getCategory());
		assertEquals(0.0, c.getConfidence(), 0.0);
	}

	@Test
	public void judgeNamesAreNormalised() {
		assertEquals("a_b_patil", KnowledgeGraphService.normaliseName("Hon'ble Mr. Justice A. B. Patil"));
		assertEquals("a_b_patil", KnowledgeGraphService.normaliseName("A.B. Patil"));
	}

	@Test
	public void issueTextHelpsClassification() {
		LegalCase c = legalCase("Complaint", "Allottee v. XYZ Developers");
		c.setIssues(Collections.singletonList(new LegalIssue("Delay in possession - interest",
				"Whether the promoter must pay interest for delayed possession", null)));
		assertEquals(CaseCategory.RERA, classifier.classify(c, null).getCategory());
	}

	@Test
	public void issueAndCitationKeysAreStable() {
		assertEquals("delay_in_possession_interest",
				new LegalIssue("Delay in Possession - Interest", "ignored when topic set", null).identifier());
		assertEquals("whether_bail_can_be_granted",
				new LegalIssue(null, "Whether bail can be granted?", null).identifier());
		assertEquals("2019_4_scc_197", NodeType.slug("(2019) 4 SCC 197"));
		assertEquals(NodeType.slug("(2019) 4 SCC 197"), NodeType.slug("2019 (4) SCC 197"));
	}
}
