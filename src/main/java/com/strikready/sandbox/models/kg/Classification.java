package com.strikready.sandbox.models.kg;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of classifying a case into a {@link CaseCategory}.
 */
public class Classification {

	private CaseCategory category;
	private String subCategory;
	private double confidence;
	private List<String> reasons = new ArrayList<>();

	public Classification() {
	}

	public Classification(CaseCategory category, String subCategory, double confidence, List<String> reasons) {
		this.category = category;
		this.subCategory = subCategory;
		this.confidence = confidence;
		this.reasons = reasons;
	}

	public CaseCategory getCategory() {
		return category;
	}

	public String getSubCategory() {
		return subCategory;
	}

	public double getConfidence() {
		return confidence;
	}

	public List<String> getReasons() {
		return reasons;
	}
}
