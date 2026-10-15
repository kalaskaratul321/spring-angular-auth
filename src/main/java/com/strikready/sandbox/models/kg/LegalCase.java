package com.strikready.sandbox.models.kg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A judgment/order in the knowledge graph. {@code category} and
 * {@code courtLevel} are stored on the document (not only as edges) so that
 * category-wise grouping is a single indexed query.
 */
@Document(collection = "kg_cases")
@CompoundIndexes({
		@CompoundIndex(name = "category_level_idx", def = "{'category': 1, 'courtLevel': 1, 'decisionDate': -1}"),
		@CompoundIndex(name = "category_verdict_idx", def = "{'category': 1, 'verdict.result': 1}"),
		@CompoundIndex(name = "source_url_idx", def = "{'source.url': 1}", unique = true, sparse = true),
		@CompoundIndex(name = "source_sha_idx", def = "{'source.sha256': 1}", sparse = true) })
public class LegalCase {

	@Id
	private String id;
	/** eCourts CNR number, unique when present. */
	@Indexed(unique = true, sparse = true)
	private String cnr;
	private String caseNumber;
	private String caseType;
	private String title;

	@Indexed
	private String courtCode;
	private CourtLevel courtLevel;

	private CaseCategory category;
	private String subCategory;
	/** Confidence of the auto-classification, 1.0 when supplied manually. */
	private double categoryConfidence;

	private Integer filingYear;
	private LocalDate decisionDate;
	private String status;
	private Verdict verdict;

	private List<String> judges = new ArrayList<>();
	private List<String> petitioners = new ArrayList<>();
	private List<String> respondents = new ArrayList<>();
	private List<StatuteReference> statutes = new ArrayList<>();
	private List<LegalIssue> issues = new ArrayList<>();
	/** Judgments this case refers to, in-graph by caseId or external by citation. */
	private List<CaseReference> references = new ArrayList<>();
	/** This judgment's own reported citations, e.g. "2024 SCC OnLine Bom 1234". */
	private List<String> citations = new ArrayList<>();
	/** Normalised {@link #citations}, maintained on ingest, used to resolve references. */
	@Indexed
	private List<String> citationKeys = new ArrayList<>();

	/** The lower-court case this matter is an appeal/revision/writ against. */
	@Indexed
	private String appealFromCaseId;

	private String summary;
	private List<String> keywords = new ArrayList<>();

	/** Official document this case was imported from; the file itself is not stored. */
	private SourceDocument source;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCnr() {
		return cnr;
	}

	public void setCnr(String cnr) {
		this.cnr = cnr;
	}

	public String getCaseNumber() {
		return caseNumber;
	}

	public void setCaseNumber(String caseNumber) {
		this.caseNumber = caseNumber;
	}

	public String getCaseType() {
		return caseType;
	}

	public void setCaseType(String caseType) {
		this.caseType = caseType;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCourtCode() {
		return courtCode;
	}

	public void setCourtCode(String courtCode) {
		this.courtCode = courtCode;
	}

	public CourtLevel getCourtLevel() {
		return courtLevel;
	}

	public void setCourtLevel(CourtLevel courtLevel) {
		this.courtLevel = courtLevel;
	}

	public CaseCategory getCategory() {
		return category;
	}

	public void setCategory(CaseCategory category) {
		this.category = category;
	}

	public String getSubCategory() {
		return subCategory;
	}

	public void setSubCategory(String subCategory) {
		this.subCategory = subCategory;
	}

	public double getCategoryConfidence() {
		return categoryConfidence;
	}

	public void setCategoryConfidence(double categoryConfidence) {
		this.categoryConfidence = categoryConfidence;
	}

	public Integer getFilingYear() {
		return filingYear;
	}

	public void setFilingYear(Integer filingYear) {
		this.filingYear = filingYear;
	}

	public LocalDate getDecisionDate() {
		return decisionDate;
	}

	public void setDecisionDate(LocalDate decisionDate) {
		this.decisionDate = decisionDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Verdict getVerdict() {
		return verdict;
	}

	public void setVerdict(Verdict verdict) {
		this.verdict = verdict;
	}

	public List<String> getJudges() {
		return judges;
	}

	public void setJudges(List<String> judges) {
		this.judges = judges;
	}

	public List<String> getPetitioners() {
		return petitioners;
	}

	public void setPetitioners(List<String> petitioners) {
		this.petitioners = petitioners;
	}

	public List<String> getRespondents() {
		return respondents;
	}

	public void setRespondents(List<String> respondents) {
		this.respondents = respondents;
	}

	public List<StatuteReference> getStatutes() {
		return statutes;
	}

	public void setStatutes(List<StatuteReference> statutes) {
		this.statutes = statutes;
	}

	public List<LegalIssue> getIssues() {
		return issues;
	}

	public void setIssues(List<LegalIssue> issues) {
		this.issues = issues;
	}

	public List<CaseReference> getReferences() {
		return references;
	}

	public void setReferences(List<CaseReference> references) {
		this.references = references;
	}

	public List<String> getCitations() {
		return citations;
	}

	public void setCitations(List<String> citations) {
		this.citations = citations;
	}

	public List<String> getCitationKeys() {
		return citationKeys;
	}

	public void setCitationKeys(List<String> citationKeys) {
		this.citationKeys = citationKeys;
	}

	public String getAppealFromCaseId() {
		return appealFromCaseId;
	}

	public void setAppealFromCaseId(String appealFromCaseId) {
		this.appealFromCaseId = appealFromCaseId;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public SourceDocument getSource() {
		return source;
	}

	public void setSource(SourceDocument source) {
		this.source = source;
	}

	public List<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(List<String> keywords) {
		this.keywords = keywords;
	}
}
