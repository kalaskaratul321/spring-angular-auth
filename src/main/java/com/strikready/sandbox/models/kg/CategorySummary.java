package com.strikready.sandbox.models.kg;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Case counts for one category, broken down by court level (High Court first).
 */
public class CategorySummary {

	private CaseCategory category;
	private String displayName;
	private long totalCases;
	private Map<CourtLevel, Long> casesByCourtLevel = new LinkedHashMap<>();
	private Map<String, Long> topStatutes = new LinkedHashMap<>();

	public CategorySummary() {
	}

	public CategorySummary(CaseCategory category) {
		this.category = category;
		this.displayName = category.getDisplayName();
	}

	public CaseCategory getCategory() {
		return category;
	}

	public void setCategory(CaseCategory category) {
		this.category = category;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public long getTotalCases() {
		return totalCases;
	}

	public void setTotalCases(long totalCases) {
		this.totalCases = totalCases;
	}

	public Map<CourtLevel, Long> getCasesByCourtLevel() {
		return casesByCourtLevel;
	}

	public void setCasesByCourtLevel(Map<CourtLevel, Long> casesByCourtLevel) {
		this.casesByCourtLevel = casesByCourtLevel;
	}

	public Map<String, Long> getTopStatutes() {
		return topStatutes;
	}

	public void setTopStatutes(Map<String, Long> topStatutes) {
		this.topStatutes = topStatutes;
	}
}
