package com.strikready.sandbox.services.kg.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.strikready.sandbox.models.kg.CaseReference;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.RelationType;
import com.strikready.sandbox.models.kg.StatuteReference;
import com.strikready.sandbox.models.kg.VerdictResult;
import org.junit.Test;

public class JudgmentParserTest {

	private final JudgmentParser parser = new JudgmentParser();

	static final String HC_JUDGMENT = String.join("\n",
			"2025:BHC-AS:12345",
			"IN THE HIGH COURT OF JUDICATURE AT BOMBAY",
			"CRIMINAL APPELLATE JURISDICTION",
			"CRIMINAL REVISION APPLICATION NO. 88 OF 2024",
			"",
			"Balasaheb Kulkarni,",
			"Age 45 years, Occ. Business,",
			"R/o Kothrud, Pune                              ...Applicant",
			"",
			"Versus",
			"",
			"A. Traders, through its proprietor            ...Respondent",
			"",
			"Mr. X, Advocate for the Applicant.",
			"",
			"CORAM : R. K. PATIL, J.",
			"RESERVED ON : 20 MAY 2025",
			"PRONOUNCED ON : 5th June, 2025",
			"",
			"JUDGMENT :",
			"1. The applicant was convicted by the learned JMFC, Pune for the offence punishable under",
			"Section 138 of the Negotiable Instruments Act, 1881 and the conviction was confirmed in appeal under",
			"Section 374 of the Cr.P.C.",
			"2. The points that arise for determination are:",
			"(i) Whether the presumption under Section 139 of the N.I. Act was rightly drawn?",
			"(ii) Whether a sentence of fine alone is adequate where compensation is paid?",
			"3. Reliance is placed on Bir Singh v. Mukesh Kumar, (2019) 4 SCC 197, which squarely applies.",
			"The decision in Rangappa v. Sri Mohan, (2010) 11 SCC 441 is also cited. The judgment in",
			"Some Party v. Another Party, 2018 SCC OnLine Bom 1234 is distinguished on facts.",
			"4. Revisional jurisdiction under Sections 397 and 401 of the Code of Criminal Procedure, 1973 is limited.",
			"",
			"ORDER",
			"(a) The Criminal Revision Application is partly allowed.",
			"(b) The conviction is maintained; the sentence of imprisonment is set aside and the fine is enhanced.",
			"",
			"(R. K. PATIL, J.)");

	@Test
	public void parsesHighCourtJudgment() {
		LegalCase c = parser.parse(HC_JUDGMENT);

		assertEquals("BHC-MUM", c.getCourtCode());
		assertEquals("Criminal Revision Application", c.getCaseType());
		assertEquals("Criminal Revision Application No. 88 of 2024", c.getCaseNumber());
		assertEquals(Integer.valueOf(2024), c.getFilingYear());
		// Addresses and "through ..." are stripped from party names.
		assertEquals("Balasaheb Kulkarni v. A. Traders", c.getTitle());
		assertEquals(1, c.getJudges().size());
		assertEquals("R. K. PATIL", c.getJudges().get(0));
		assertEquals(LocalDate.of(2025, 6, 5), c.getDecisionDate());
		assertEquals("2025:BHC-AS:12345", c.getCitations().get(0));

		List<String> statutes = new ArrayList<>();
		for (StatuteReference s : c.getStatutes()) {
			statutes.add(s.identifier());
		}
		assertTrue(statutes.toString(), statutes.contains("NI_ACT:138"));
		assertTrue(statutes.toString(), statutes.contains("NI_ACT:139"));
		assertTrue(statutes.toString(), statutes.contains("CRPC:374"));
		assertTrue(statutes.toString(), statutes.contains("CRPC:397"));
		assertTrue(statutes.toString(), statutes.contains("CRPC:401"));

		assertEquals(2, c.getIssues().size());
		assertEquals("Whether the presumption under Section 139 of the N.I. Act was rightly drawn?",
				c.getIssues().get(0).getQuestion());

		assertNotNull(c.getVerdict());
		assertEquals(VerdictResult.PARTLY_ALLOWED, c.getVerdict().getResult());
		assertTrue(c.getVerdict().getSummary().contains("partly allowed"));

		assertEquals(3, c.getReferences().size());
		CaseReference bir = find(c.getReferences(), "(2019) 4 SCC 197");
		assertEquals(RelationType.FOLLOWS, bir.getTreatment());
		assertEquals("Bir Singh v. Mukesh Kumar", bir.getTitle());
		assertEquals(RelationType.CITES, find(c.getReferences(), "(2010) 11 SCC 441").getTreatment());
		assertEquals(RelationType.DISTINGUISHES, find(c.getReferences(), "2018 SCC OnLine Bom 1234").getTreatment());
	}

	@Test
	public void parsesNagpurBenchAndAcquittal() {
		String text = String.join("\n", "IN THE HIGH COURT OF JUDICATURE AT BOMBAY", "NAGPUR BENCH, NAGPUR",
				"CRIMINAL APPEAL NO. 312 OF 2019", "State of Maharashtra ...Appellant", "V/s.",
				"Ramesh Shinde ...Respondent", "CORAM : SMT. A. B. DESHMUKH AND C. D. JOSHI, JJ.",
				"DATE : 12/03/2024",
				"The accused was tried for offences punishable under Sections 302, 307 r/w 34 of the IPC.",
				"In the result, the appeal is dismissed. The order of acquittal is confirmed.",
				"The accused is acquitted of all charges.");
		LegalCase c = parser.parse(text);
		assertEquals("BHC-NAG", c.getCourtCode());
		assertEquals("State of Maharashtra v. Ramesh Shinde", c.getTitle());
		assertEquals(Arrays.asList("SMT. A. B. DESHMUKH", "C. D. JOSHI"), c.getJudges());
		assertEquals(LocalDate.of(2024, 3, 12), c.getDecisionDate());
		assertEquals(VerdictResult.ACQUITTED, c.getVerdict().getResult());
		List<String> ids = new ArrayList<>();
		for (StatuteReference s : c.getStatutes()) {
			ids.add(s.identifier());
		}
		assertTrue(ids.toString(), ids.contains("IPC:302") && ids.contains("IPC:307") && ids.contains("IPC:34"));
	}

	@Test
	public void parsesMahaReraOrder() {
		String text = String.join("\n", "BEFORE THE MAHARASHTRA REAL ESTATE REGULATORY AUTHORITY", "MUMBAI",
				"COMPLAINT NO. CC006000000123456", "Mr. Allottee ...Complainant", "Versus",
				"XYZ Developers Pvt. Ltd. ...Respondent", "MEMBER : SHRI M. K. SHARMA",
				"The complainant seeks interest under section 18 of the Real Estate (Regulation and Development) Act, 2016.",
				"Whether the complainant is entitled to interest for delayed possession?",
				"ORDER", "The complaint is allowed. The respondent shall pay interest.", "Date: 12.09.2024");
		LegalCase c = parser.parse(text);
		assertEquals("MAHARERA", c.getCourtCode());
		assertEquals("CC006000000123456", c.getCaseNumber());
		assertEquals("Mr. Allottee v. XYZ Developers Pvt. Ltd", c.getTitle());
		assertEquals("RERA:18", c.getStatutes().get(0).identifier());
		assertEquals(VerdictResult.ALLOWED, c.getVerdict().getResult());
		assertEquals(LocalDate.of(2024, 9, 12), c.getDecisionDate());
		assertEquals(1, c.getIssues().size());
	}

	@Test
	public void unknownTextYieldsNothingWrong() {
		LegalCase c = parser.parse("Notice regarding court vacation.");
		assertNull(c.getCourtCode());
		assertNull(c.getCaseNumber());
		assertNull(c.getVerdict());
		assertTrue(c.getStatutes().isEmpty());
		assertTrue(c.getReferences().isEmpty());
	}

	@Test
	public void canonicalisesFullActNames() {
		assertEquals("CRPC:125", new StatuteReference("Code of Criminal Procedure, 1973", "125(1)").identifier());
		assertEquals("IPC:498A", new StatuteReference("the Indian Penal Code", "498-A").identifier());
		assertEquals("BNSS:144", new StatuteReference("Bharatiya Nagarik Suraksha Sanhita, 2023", "144").identifier());
		assertEquals("MLRC:247", new StatuteReference("Maharashtra Land Revenue Code, 1966", "247").identifier());
	}

	private static CaseReference find(List<CaseReference> refs, String citation) {
		for (CaseReference r : refs) {
			if (r.getCitation().equals(citation)) {
				return r;
			}
		}
		throw new AssertionError("No reference " + citation + " in " + refs.size() + " refs");
	}
}
