package com.strikready.sandbox.models.kg;

/**
 * Top-level practice-area categories used to group the case knowledge graph.
 * Every case belongs to exactly one category; finer grouping goes in
 * {@link LegalCase#getSubCategory()}.
 */
public enum CaseCategory {

	CIVIL("Civil", "Suits for money, property, injunctions, contracts, specific performance, partition"),
	CRIMINAL("Criminal", "Offences under IPC/BNS, CrPC/BNSS, NDPS, NI Act s.138, bail, trials and appeals"),
	LAND("Land & Revenue", "Land revenue, tenancy, mutation, ceiling, acquisition, 7/12 extract disputes"),
	RERA("RERA / Real Estate", "MahaRERA complaints, possession delay, refunds, interest, builder-buyer disputes"),
	FAMILY("Family & Matrimonial", "Divorce, maintenance, custody, domestic violence, succession within family"),
	CONSUMER("Consumer", "Deficiency in service, unfair trade practice before consumer commissions"),
	LABOUR_SERVICE("Labour & Service", "Industrial disputes, wages, service matters of employees"),
	CONSTITUTIONAL("Constitutional / Writ", "Writ petitions, PILs, fundamental rights, judicial review"),
	COMMERCIAL("Commercial & Arbitration", "Commercial suits, arbitration, company and insolvency matters"),
	TAX("Tax", "Income tax, GST, stamp duty and other fiscal matters"),
	OTHER("Other", "Not yet classified");

	private final String displayName;
	private final String description;

	CaseCategory(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}
}
