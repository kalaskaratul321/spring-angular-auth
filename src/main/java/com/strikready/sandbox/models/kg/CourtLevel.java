package com.strikready.sandbox.models.kg;

/**
 * Position of a forum in the judicial hierarchy, from High Court down to the
 * trial courts and quasi-judicial authorities. {@link #getRank()} is higher
 * for superior forums, so sorting by rank orders an appeal chain bottom-up.
 */
public enum CourtLevel {

	HIGH_COURT("High Court", 100),
	APPELLATE_TRIBUNAL("Appellate Tribunal", 80),
	DISTRICT_COURT("District Court", 60),
	SESSIONS_COURT("Sessions Court", 60),
	FAMILY_COURT("Family Court", 55),
	STATE_COMMISSION("State Commission", 55),
	TRIBUNAL("Tribunal / Authority", 50),
	REVENUE_APPELLATE("Revenue Appellate Authority", 45),
	CIVIL_JUDGE_SENIOR_DIVISION("Civil Judge (Senior Division)", 40),
	DISTRICT_COMMISSION("District Commission", 35),
	CIVIL_JUDGE_JUNIOR_DIVISION("Civil Judge (Junior Division)", 30),
	JUDICIAL_MAGISTRATE("Judicial Magistrate First Class", 30),
	REVENUE_AUTHORITY("Revenue Authority", 20);

	private final String displayName;
	private final int rank;

	CourtLevel(String displayName, int rank) {
		this.displayName = displayName;
		this.rank = rank;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getRank() {
		return rank;
	}
}
