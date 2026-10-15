package com.strikready.sandbox.models.kg;

/**
 * A question of law or fact the court decided. {@code topic} is a short
 * canonical label ("Delay in possession - interest") used as the ISSUE node
 * key, so different cases deciding the same point meet at one node. When
 * {@code topic} is blank the question itself is used.
 */
public class LegalIssue {

	private static final int MAX_KEY_LENGTH = 120;

	private String topic;
	private String question;
	/** How the court answered the question. */
	private String finding;

	public LegalIssue() {
	}

	public LegalIssue(String topic, String question, String finding) {
		this.topic = topic;
		this.question = question;
		this.finding = finding;
	}

	/** Node identifier for this issue, or empty if it has neither topic nor question. */
	public String identifier() {
		String label = topic != null && !topic.trim().isEmpty() ? topic : question;
		String slug = NodeType.slug(label);
		return slug.length() > MAX_KEY_LENGTH ? slug.substring(0, MAX_KEY_LENGTH) : slug;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getFinding() {
		return finding;
	}

	public void setFinding(String finding) {
		this.finding = finding;
	}
}
