package com.strikready.sandbox.models.kg;

/**
 * Kinds of edges in the case knowledge graph.
 */
public enum RelationType {
	/** CASE -> COURT */
	HEARD_IN,
	/** CASE -> CATEGORY */
	BELONGS_TO,
	/** CASE -> STATUTE */
	INVOKES,
	/** CASE -> JUDGE */
	DECIDED_BY,
	/** CASE -> PARTY */
	INVOLVES_PARTY,
	/** higher CASE -> lower CASE it arose from (e.g. HC appeal -> Sessions judgment) */
	APPEAL_FROM,
	/** CASE -> ISSUE */
	RAISES_ISSUE,
	/** CASE -> VERDICT */
	HAS_VERDICT,
	/** CASE -> referred CASE or CITATION */
	CITES,
	/** CASE -> referred CASE or CITATION */
	FOLLOWS,
	/** CASE -> referred CASE or CITATION */
	DISTINGUISHES,
	/** CASE -> referred CASE or CITATION */
	OVERRULES,
	/** lower COURT -> appellate COURT */
	APPEALS_TO;

	public boolean isReferenceTreatment() {
		return this == CITES || this == FOLLOWS || this == DISTINGUISHES || this == OVERRULES;
	}
}
