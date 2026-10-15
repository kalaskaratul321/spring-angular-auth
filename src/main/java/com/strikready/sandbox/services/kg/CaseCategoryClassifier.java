package com.strikready.sandbox.services.kg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.Classification;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.LegalIssue;
import com.strikready.sandbox.models.kg.StatuteReference;
import org.springframework.stereotype.Component;

/**
 * Rule-based classifier that assigns a {@link CaseCategory} (and a
 * sub-category where possible) to a case from the statutes it invokes, its
 * case type, the forum it was decided in and keywords in its title, summary and issues.
 *
 * Signals are weighted: a specific section (e.g. CrPC 125 -> Family) beats a
 * whole act, which beats the case type, which beats free-text keywords. The
 * class is deliberately self-contained so an LLM-backed classifier can later
 * replace or vote alongside it.
 */
@Component
public class CaseCategoryClassifier {

	private static final int SECTION_WEIGHT = 6;
	private static final int SINGLE_CATEGORY_COURT_WEIGHT = 5;
	private static final int ACT_WEIGHT = 3;
	private static final int CASE_TYPE_WEIGHT = 2;
	private static final int KEYWORD_WEIGHT = 1;

	private static final List<Rule> SECTION_RULES = new ArrayList<>();
	private static final List<Rule> ACT_RULES = new ArrayList<>();
	private static final List<Rule> CASE_TYPE_RULES = new ArrayList<>();
	private static final List<Rule> KEYWORD_RULES = new ArrayList<>();

	static {
		// Specific provisions whose category differs from (or refines) their parent act.
		section(CaseCategory.FAMILY, "Maintenance", "CRPC:125", "BNSS:144");
		section(CaseCategory.CRIMINAL, "Bail", "CRPC:437", "CRPC:438", "CRPC:439", "BNSS:480", "BNSS:482", "BNSS:483");
		section(CaseCategory.CRIMINAL, "Quashing", "CRPC:482", "BNSS:528");
		section(CaseCategory.CRIMINAL, "Cheque Dishonour", "NI_ACT:138", "NEGOTIABLE_INSTRUMENTS_ACT:138");
		section(CaseCategory.FAMILY, "Cruelty / Dowry", "IPC:498A", "BNS:85", "BNS:86");
		section(CaseCategory.CONSTITUTIONAL, "Writ", "CONSTITUTION:226", "CONSTITUTION:227", "CONSTITUTION:32");

		act(CaseCategory.CRIMINAL, null, "IPC", "BNS", "CRPC", "BNSS", "INDIAN PENAL CODE", "BHARATIYA NYAYA SANHITA",
				"BHARATIYA NAGARIK SURAKSHA SANHITA", "CODE OF CRIMINAL PROCEDURE");
		act(CaseCategory.CRIMINAL, "NDPS", "NDPS", "NARCOTIC DRUGS");
		act(CaseCategory.CRIMINAL, "Organised Crime", "MCOCA", "UAPA");
		act(CaseCategory.CRIMINAL, "POCSO", "POCSO");
		act(CaseCategory.CRIMINAL, "Corruption", "PC ACT", "PREVENTION OF CORRUPTION");
		act(CaseCategory.CRIMINAL, "Atrocities", "SC ST", "ATROCITIES");
		act(CaseCategory.CRIMINAL, "Arms", "ARMS ACT");

		act(CaseCategory.CIVIL, null, "CPC", "CODE OF CIVIL PROCEDURE", "LIMITATION ACT", "EVIDENCE ACT", "BSA");
		act(CaseCategory.CIVIL, "Specific Performance", "SPECIFIC RELIEF");
		act(CaseCategory.CIVIL, "Contract", "CONTRACT ACT");
		act(CaseCategory.CIVIL, "Property", "TRANSFER OF PROPERTY", "TPA", "EASEMENTS");
		act(CaseCategory.CIVIL, "Tenancy / Rent", "RENT CONTROL", "MRCA");
		act(CaseCategory.CIVIL, "Succession", "HINDU SUCCESSION", "SUCCESSION ACT");

		act(CaseCategory.LAND, "Land Revenue", "MLRC", "LAND REVENUE CODE");
		act(CaseCategory.LAND, "Agricultural Tenancy", "MTAL", "TENANCY AND AGRICULTURAL LANDS");
		act(CaseCategory.LAND, "Land Acquisition", "LAND ACQUISITION", "RFCTLARR", "LARR");
		act(CaseCategory.LAND, "Ceiling", "CEILING ON HOLDINGS", "ULCRA");
		act(CaseCategory.LAND, "Town Planning", "MRTP", "REGIONAL AND TOWN PLANNING");
		act(CaseCategory.LAND, "Fragmentation", "FRAGMENTATION");

		act(CaseCategory.RERA, "RERA", "RERA", "REAL ESTATE");
		act(CaseCategory.RERA, "MOFA", "MOFA", "OWNERSHIP FLATS");

		act(CaseCategory.FAMILY, "Matrimonial", "HMA", "HINDU MARRIAGE", "SPECIAL MARRIAGE", "DIVORCE ACT");
		act(CaseCategory.FAMILY, "Domestic Violence", "PWDVA", "DV ACT", "DOMESTIC VIOLENCE");
		act(CaseCategory.FAMILY, "Custody", "GUARDIANS AND WARDS", "HMGA");
		act(CaseCategory.FAMILY, "Maintenance", "HAMA", "ADOPTIONS AND MAINTENANCE");
		act(CaseCategory.FAMILY, null, "FAMILY COURTS");

		act(CaseCategory.CONSUMER, null, "CONSUMER PROTECTION", "CPA");

		act(CaseCategory.LABOUR_SERVICE, "Industrial Dispute", "INDUSTRIAL DISPUTES", "ID ACT", "MRTU", "PULP");
		act(CaseCategory.LABOUR_SERVICE, "Wages & Benefits", "GRATUITY", "MINIMUM WAGES", "PAYMENT OF WAGES", "EPF",
				"EMPLOYEES COMPENSATION", "WORKMEN S COMPENSATION");
		act(CaseCategory.LABOUR_SERVICE, "Service", "MCSR", "CIVIL SERVICES");

		act(CaseCategory.CONSTITUTIONAL, null, "CONSTITUTION");

		act(CaseCategory.COMMERCIAL, "Arbitration", "ARBITRATION");
		act(CaseCategory.COMMERCIAL, null, "COMMERCIAL COURTS");
		act(CaseCategory.COMMERCIAL, "Company / Insolvency", "COMPANIES ACT", "IBC", "INSOLVENCY");

		act(CaseCategory.TAX, "Income Tax", "INCOME TAX");
		act(CaseCategory.TAX, "GST", "GST", "CGST", "MGST", "IGST");
		act(CaseCategory.TAX, "Stamp Duty", "STAMP ACT");
		act(CaseCategory.TAX, "Customs", "CUSTOMS");

		caseType(CaseCategory.CRIMINAL, null, "CRIMINAL APPEAL", "CRIMINAL REVISION", "CRIMINAL APPLICATION",
				"CRIMINAL WRIT", "SESSIONS CASE", "SPECIAL CASE", "RCC", "SCC", "SUMMARY CRIMINAL");
		caseType(CaseCategory.CRIMINAL, "Bail", "BAIL APPLICATION", "ANTICIPATORY BAIL");
		caseType(CaseCategory.CIVIL, null, "REGULAR CIVIL SUIT", "SPECIAL CIVIL SUIT", "RCS", "SCS", "FIRST APPEAL",
				"SECOND APPEAL", "CIVIL REVISION", "CIVIL APPEAL", "REGULAR CIVIL APPEAL", "EXECUTION PETITION");
		caseType(CaseCategory.LAND, null, "RTS APPEAL", "TENANCY APPEAL", "REVENUE APPEAL", "LAND ACQUISITION REFERENCE",
				"LAR");
		caseType(CaseCategory.RERA, null, "RERA COMPLAINT", "RERA APPEAL");
		caseType(CaseCategory.FAMILY, null, "PETITION A", "PETITION E", "MARRIAGE PETITION", "HINDU MARRIAGE PETITION",
				"FAMILY COURT APPEAL");
		caseType(CaseCategory.CONSUMER, null, "CONSUMER COMPLAINT", "CONSUMER APPEAL");
		caseType(CaseCategory.LABOUR_SERVICE, null, "REFERENCE IDA", "ULP COMPLAINT");
		caseType(CaseCategory.CONSTITUTIONAL, "Writ", "WRIT PETITION", "PIL", "PUBLIC INTEREST LITIGATION");
		caseType(CaseCategory.COMMERCIAL, null, "COMMERCIAL SUIT", "COMMERCIAL APPEAL", "ARBITRATION PETITION",
				"COMMERCIAL ARBITRATION");
		caseType(CaseCategory.TAX, null, "INCOME TAX APPEAL", "TAX APPEAL", "SALES TAX REFERENCE");

		keyword(CaseCategory.CRIMINAL, "Bail", "bail", "anticipatory bail");
		keyword(CaseCategory.CRIMINAL, null, "fir", "accused", "conviction", "acquittal", "acquitted", "chargesheet",
				"charge sheet", "murder", "prosecution", "offence", "police station");
		keyword(CaseCategory.LAND, null, "7/12", "satbara", "mutation", "ferfar", "agricultural land",
				"revenue record", "survey no", "gat no", "tahsildar", "collector", "land acquisition", "tenant cultivator");
		keyword(CaseCategory.RERA, null, "rera", "maharera", "promoter", "allottee", "builder", "possession of flat",
				"occupancy certificate", "delayed possession");
		keyword(CaseCategory.FAMILY, null, "divorce", "maintenance", "custody", "matrimonial", "domestic violence",
				"alimony", "restitution of conjugal rights", "wife", "husband");
		keyword(CaseCategory.CIVIL, null, "injunction", "specific performance", "partition", "declaration", "decree",
				"recovery of money", "plaintiff", "defendant", "possession suit");
		keyword(CaseCategory.CONSUMER, null, "deficiency in service", "consumer", "unfair trade practice");
		keyword(CaseCategory.LABOUR_SERVICE, null, "workman", "reinstatement", "back wages", "gratuity",
				"industrial dispute", "pension", "disciplinary enquiry", "termination of service");
		keyword(CaseCategory.CONSTITUTIONAL, null, "article 226", "article 14", "article 21", "fundamental right",
				"public interest litigation", "judicial review");
		keyword(CaseCategory.COMMERCIAL, null, "arbitral award", "arbitration", "insolvency", "winding up",
				"commercial dispute");
		keyword(CaseCategory.TAX, null, "assessment year", "reassessment", "income tax", "gst", "stamp duty",
				"input tax credit");
	}

	public Classification classify(LegalCase legalCase, Collection<CaseCategory> courtCategories) {
		Map<CaseCategory, Integer> scores = new EnumMap<>(CaseCategory.class);
		Map<CaseCategory, Integer> bestSubWeight = new EnumMap<>(CaseCategory.class);
		Map<CaseCategory, String> subCategories = new EnumMap<>(CaseCategory.class);
		List<String> reasons = new ArrayList<>();

		if (courtCategories != null && courtCategories.size() == 1) {
			CaseCategory only = courtCategories.iterator().next();
			add(scores, bestSubWeight, subCategories, only, null, SINGLE_CATEGORY_COURT_WEIGHT);
			reasons.add("Forum hears only " + only.getDisplayName() + " matters");
		}

		if (legalCase.getStatutes() != null) {
			for (StatuteReference ref : legalCase.getStatutes()) {
				String id = ref.identifier();
				boolean sectionMatched = false;
				for (Rule rule : SECTION_RULES) {
					if (rule.matchesExact(id)) {
						add(scores, bestSubWeight, subCategories, rule.category, rule.subCategory, SECTION_WEIGHT);
						reasons.add("Provision " + id + " -> " + rule.category.getDisplayName());
						sectionMatched = true;
					}
				}
				if (sectionMatched) {
					continue;
				}
				String act = normaliseTokens(ref.getAct());
				for (Rule rule : ACT_RULES) {
					if (rule.matchesTokens(act)) {
						add(scores, bestSubWeight, subCategories, rule.category, rule.subCategory, ACT_WEIGHT);
						reasons.add("Act " + ref.getAct() + " -> " + rule.category.getDisplayName());
						break;
					}
				}
			}
		}

		String caseType = normaliseTokens(legalCase.getCaseType());
		for (Rule rule : CASE_TYPE_RULES) {
			if (rule.matchesTokens(caseType)) {
				add(scores, bestSubWeight, subCategories, rule.category, rule.subCategory, CASE_TYPE_WEIGHT);
				reasons.add("Case type '" + legalCase.getCaseType() + "' -> " + rule.category.getDisplayName());
				break;
			}
		}

		String text = buildText(legalCase);
		for (Rule rule : KEYWORD_RULES) {
			for (int i = 0; i < rule.patterns.size(); i++) {
				if (rule.patterns.get(i).matcher(text).find()) {
					add(scores, bestSubWeight, subCategories, rule.category, rule.subCategory, KEYWORD_WEIGHT);
					reasons.add("Keyword '" + rule.aliases[i] + "' -> " + rule.category.getDisplayName());
				}
			}
		}

		CaseCategory winner = CaseCategory.OTHER;
		int best = 0;
		int total = 0;
		for (Map.Entry<CaseCategory, Integer> e : scores.entrySet()) {
			total += e.getValue();
			if (e.getValue() > best) {
				best = e.getValue();
				winner = e.getKey();
			}
		}
		double confidence = total == 0 ? 0.0 : Math.round(1000.0 * best / total) / 1000.0;
		return new Classification(winner, subCategories.get(winner), confidence, reasons);
	}

	private static void add(Map<CaseCategory, Integer> scores, Map<CaseCategory, Integer> bestSubWeight,
			Map<CaseCategory, String> subCategories, CaseCategory category, String subCategory, int weight) {
		Integer current = scores.get(category);
		scores.put(category, current == null ? weight : current + weight);
		if (subCategory != null) {
			Integer prev = bestSubWeight.get(category);
			if (prev == null || weight > prev) {
				bestSubWeight.put(category, weight);
				subCategories.put(category, subCategory);
			}
		}
	}

	private static String buildText(LegalCase c) {
		StringBuilder sb = new StringBuilder();
		append(sb, c.getTitle());
		append(sb, c.getSummary());
		append(sb, c.getSubCategory());
		if (c.getKeywords() != null) {
			for (String k : c.getKeywords()) {
				append(sb, k);
			}
		}
		if (c.getIssues() != null) {
			for (LegalIssue issue : c.getIssues()) {
				append(sb, issue.getTopic());
				append(sb, issue.getQuestion());
			}
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	private static void append(StringBuilder sb, String s) {
		if (s != null) {
			sb.append(' ').append(s);
		}
	}

	/** Upper-cases and collapses punctuation so "Cr.P.C." and "CrPC" both become " CRPC ". */
	static String normaliseTokens(String s) {
		if (s == null) {
			return " ";
		}
		String upper = s.toUpperCase(Locale.ROOT).replace(".", "");
		return " " + upper.replaceAll("[^A-Z0-9]+", " ").trim() + " ";
	}

	private static void section(CaseCategory category, String sub, String... ids) {
		SECTION_RULES.add(new Rule(category, sub, ids, false));
	}

	private static void act(CaseCategory category, String sub, String... aliases) {
		ACT_RULES.add(new Rule(category, sub, aliases, false));
	}

	private static void caseType(CaseCategory category, String sub, String... aliases) {
		CASE_TYPE_RULES.add(new Rule(category, sub, aliases, false));
	}

	private static void keyword(CaseCategory category, String sub, String... words) {
		KEYWORD_RULES.add(new Rule(category, sub, words, true));
	}

	private static final class Rule {
		final CaseCategory category;
		final String subCategory;
		final String[] aliases;
		final List<Pattern> patterns = new ArrayList<>();

		Rule(CaseCategory category, String subCategory, String[] aliases, boolean keyword) {
			this.category = category;
			this.subCategory = subCategory;
			if (keyword) {
				this.aliases = aliases;
				for (String w : aliases) {
					patterns.add(Pattern.compile("\\b" + Pattern.quote(w) + "\\b"));
				}
			} else {
				this.aliases = new String[aliases.length];
				for (int i = 0; i < aliases.length; i++) {
					this.aliases[i] = aliases[i].contains(":") ? aliases[i] : normaliseTokens(aliases[i]);
				}
			}
		}

		boolean matchesExact(String identifier) {
			for (String a : aliases) {
				if (a.equals(identifier)) {
					return true;
				}
			}
			return false;
		}

		boolean matchesTokens(String normalised) {
			for (String a : aliases) {
				if (normalised.contains(a)) {
					return true;
				}
			}
			return false;
		}
	}
}
