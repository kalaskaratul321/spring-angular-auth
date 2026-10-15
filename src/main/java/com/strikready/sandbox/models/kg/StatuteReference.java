package com.strikready.sandbox.models.kg;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A provision relied on in a case, e.g. act "IPC", section "302".
 */
public class StatuteReference {

	/** Full act names (normalised) mapped to the short form used in node keys. */
	private static final Map<String, String> ACT_ALIASES = new HashMap<>();

	static {
		alias("IPC", "INDIAN_PENAL_CODE");
		alias("CRPC", "CODE_OF_CRIMINAL_PROCEDURE", "CRIMINAL_PROCEDURE_CODE");
		alias("CPC", "CODE_OF_CIVIL_PROCEDURE", "CIVIL_PROCEDURE_CODE");
		alias("BNS", "BHARATIYA_NYAYA_SANHITA");
		alias("BNSS", "BHARATIYA_NAGARIK_SURAKSHA_SANHITA");
		alias("BSA", "BHARATIYA_SAKSHYA_ADHINIYAM");
		alias("NI_ACT", "NEGOTIABLE_INSTRUMENTS_ACT", "NI");
		alias("EVIDENCE_ACT", "INDIAN_EVIDENCE_ACT");
		alias("RERA", "REAL_ESTATE_REGULATION_AND_DEVELOPMENT_ACT", "RERA_ACT");
		alias("MLRC", "MAHARASHTRA_LAND_REVENUE_CODE");
		alias("MTAL", "MAHARASHTRA_TENANCY_AND_AGRICULTURAL_LANDS_ACT", "BOMBAY_TENANCY_AND_AGRICULTURAL_LANDS_ACT");
		alias("NDPS", "NARCOTIC_DRUGS_AND_PSYCHOTROPIC_SUBSTANCES_ACT", "NDPS_ACT");
		alias("HMA", "HINDU_MARRIAGE_ACT");
		alias("PWDVA", "PROTECTION_OF_WOMEN_FROM_DOMESTIC_VIOLENCE_ACT", "DV_ACT");
		alias("CONSTITUTION", "CONSTITUTION_OF_INDIA");
	}

	private String act;
	private String section;
	/** Official text of the provision, e.g. an India Code URL. */
	private String sourceUrl;

	public StatuteReference() {
	}

	public StatuteReference(String act, String section) {
		this.act = act;
		this.section = section;
	}

	/**
	 * Normalised STATUTE node identifier: "Cr.P.C." / "439" -> {@code CRPC:439},
	 * "Code of Criminal Procedure, 1973" / "125(1)" -> {@code CRPC:125},
	 * "IPC" / "498-A" -> {@code IPC:498A}.
	 */
	public String identifier() {
		String a = canonicalAct(act);
		String s = section == null ? ""
				: section.toUpperCase(Locale.ROOT).replaceAll("\\(.*?\\)", "").replaceAll("[^A-Z0-9]", "");
		return s.isEmpty() ? a : a + ":" + s;
	}

	public static String canonicalAct(String act) {
		if (act == null) {
			return "";
		}
		String a = act.toUpperCase(Locale.ROOT).replace(".", "").replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_|_$", "").replaceAll("^THE_", "").replaceAll("_?(1[6-9]|20)\\d{2}$", "");
		String alias = ACT_ALIASES.get(a);
		return alias != null ? alias : a;
	}

	private static void alias(String canonical, String... longForms) {
		for (String l : longForms) {
			ACT_ALIASES.put(l, canonical);
		}
	}

	public String getAct() {
		return act;
	}

	public void setAct(String act) {
		this.act = act;
	}

	public String getSection() {
		return section;
	}

	public void setSection(String section) {
		this.section = section;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}
}
