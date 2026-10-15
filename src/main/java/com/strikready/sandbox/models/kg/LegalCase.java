package com.strikready.sandbox.models.kg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A judgment/order in the knowledge graph. {@code category} and
 * {@code courtLevel} are stored on the document (not only as edges) so that
 * category-wise grouping is a single indexed query.
 */
@Document(collection = "kg_cases")
@CompoundIndex(name = "category_level_idx", def = "{'category': 1, 'courtLevel': 1, 'decisionDate': -1}")
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
	private String outcome;

	private List<String> judges = new ArrayList<>();
	private List<String> petitioners = new ArrayList<>();
	private List<String> respondents = new ArrayList<>();
	private List<StatuteReference> statutes = new ArrayList<>();
	private List<PrecedentLink> precedents = new ArrayList<>();

	/** The lower-court case this matter is an appeal/revision/writ against. */
	@Indexed
	private String appealFromCaseId;

	private String summary;
	private List<String> keywords = new ArrayList<>();

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

	public String getOutcome() {
		return outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
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

	public List<PrecedentLink> getPrecedents() {
		return precedents;
	}

	public void setPrecedents(List<PrecedentLink> precedents) {
		this.precedents = precedents;
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

	public List<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(List<String> keywords) {
		this.keywords = keywords;
	}
}
