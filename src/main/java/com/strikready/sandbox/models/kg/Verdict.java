package com.strikready.sandbox.models.kg;

/**
 * The decision in a case: a normalised {@link VerdictResult} plus the
 * operative order in the court's words.
 */
public class Verdict {

	private VerdictResult result;
	/** Operative part of the order, e.g. "Promoter to pay interest at SBI MCLR + 2% from 1.1.2020". */
	private String summary;
	private String reliefGranted;
	/** Sentence or fine for criminal matters. */
	private String sentence;

	public Verdict() {
	}

	public Verdict(VerdictResult result, String summary) {
		this.result = result;
		this.summary = summary;
	}

	public VerdictResult getResult() {
		return result;
	}

	public void setResult(VerdictResult result) {
		this.result = result;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getReliefGranted() {
		return reliefGranted;
	}

	public void setReliefGranted(String reliefGranted) {
		this.reliefGranted = reliefGranted;
	}

	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}
}
