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
	/** CASE -> precedent CASE */
	CITES,
	/** CASE -> precedent CASE */
	FOLLOWS,
	/** CASE -> precedent CASE */
	DISTINGUISHES,
	/** CASE -> precedent CASE */
	OVERRULES,
	/** lower COURT -> appellate COURT */
	APPEALS_TO
}
