package com.strikready.sandbox.models.kg;

/**
 * A reference between two judgments as returned by the API: the treatment and
 * the referred (or referring) judgment, resolved to a case in the graph where
 * possible.
 */
public class ReferenceView {

	private RelationType treatment;
	private String citation;
	private String title;
	private String paragraph;
	/** Official URL of the referred judgment, from the reference or the imported case. */
	private String sourceUrl;
	/** Set when the judgment is in the graph. */
	private String caseId;
	private String courtCode;
	private CourtLevel courtLevel;
	private VerdictResult verdict;

	public static ReferenceView of(RelationType treatment, LegalCase c) {
		ReferenceView v = new ReferenceView();
		v.treatment = treatment;
		v.fill(c);
		return v;
	}

	public static ReferenceView of(CaseReference ref, LegalCase resolved) {
		ReferenceView v = new ReferenceView();
		v.treatment = ref.getTreatment() == null ? RelationType.CITES : ref.getTreatment();
		v.citation = ref.getCitation();
		v.title = ref.getTitle();
		v.paragraph = ref.getParagraph();
		v.courtCode = ref.getCourt();
		v.sourceUrl = ref.getSourceUrl();
		if (resolved != null) {
			v.fill(resolved);
		}
		return v;
	}

	private void fill(LegalCase c) {
		caseId = c.getId();
		title = c.getTitle() != null ? c.getTitle() : title;
		if (citation == null && c.getCitations() != null && !c.getCitations().isEmpty()) {
			citation = c.getCitations().get(0);
		}
		courtCode = c.getCourtCode();
		courtLevel = c.getCourtLevel();
		verdict = c.getVerdict() == null ? null : c.getVerdict().getResult();
		if (c.getSource() != null) {
			sourceUrl = c.getSource().getUrl();
		}
	}

	public RelationType getTreatment() {
		return treatment;
	}

	public String getCitation() {
		return citation;
	}

	public String getTitle() {
		return title;
	}

	public String getParagraph() {
		return paragraph;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getCaseId() {
		return caseId;
	}

	public String getCourtCode() {
		return courtCode;
	}

	public CourtLevel getCourtLevel() {
		return courtLevel;
	}

	public VerdictResult getVerdict() {
		return verdict;
	}
}
