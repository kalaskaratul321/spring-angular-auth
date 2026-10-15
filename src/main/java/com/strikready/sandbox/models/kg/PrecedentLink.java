package com.strikready.sandbox.models.kg;

/**
 * A reference from one case to an earlier case, with how it was treated.
 * {@code relation} must be one of CITES, FOLLOWS, DISTINGUISHES or OVERRULES.
 */
public class PrecedentLink {

	private String caseId;
	private RelationType relation = RelationType.CITES;

	public PrecedentLink() {
	}

	public PrecedentLink(String caseId, RelationType relation) {
		this.caseId = caseId;
		this.relation = relation;
	}

	public String getCaseId() {
		return caseId;
	}

	public void setCaseId(String caseId) {
		this.caseId = caseId;
	}

	public RelationType getRelation() {
		return relation;
	}

	public void setRelation(RelationType relation) {
		this.relation = relation;
	}
}
