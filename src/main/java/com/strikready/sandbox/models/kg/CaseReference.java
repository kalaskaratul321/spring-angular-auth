package com.strikready.sandbox.models.kg;

/**
 * A judgment referred to by a case. Either {@code caseId} (a case already in
 * the graph) or {@code citation} (e.g. "(2019) 5 SCC 123", "AIR 1980 SC 1789")
 * identifies it. A citation that matches a case's own {@code citations} is
 * linked to that case; otherwise it becomes a shared CITATION node, which is
 * re-pointed to the case if it is ingested later.
 *
 * {@code treatment} must be one of CITES, FOLLOWS, DISTINGUISHES or OVERRULES.
 */
public class CaseReference {

	private String caseId;
	private String citation;
	private String title;
	private String court;
	private RelationType treatment = RelationType.CITES;
	/** Paragraph(s) of the judgment where it is referred to. */
	private String paragraph;
	/** Official URL of the referred judgment; import it to add it to the graph. */
	private String sourceUrl;

	public CaseReference() {
	}

	public CaseReference(String caseId, String citation, RelationType treatment) {
		this.caseId = caseId;
		this.citation = citation;
		this.treatment = treatment;
	}

	public String getCaseId() {
		return caseId;
	}

	public void setCaseId(String caseId) {
		this.caseId = caseId;
	}

	public String getCitation() {
		return citation;
	}

	public void setCitation(String citation) {
		this.citation = citation;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCourt() {
		return court;
	}

	public void setCourt(String court) {
		this.court = court;
	}

	public RelationType getTreatment() {
		return treatment;
	}

	public void setTreatment(RelationType treatment) {
		this.treatment = treatment;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getParagraph() {
		return paragraph;
	}

	public void setParagraph(String paragraph) {
		this.paragraph = paragraph;
	}
}
