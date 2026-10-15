package com.strikready.sandbox.models.kg;

/**
 * Normalised disposal of a case, so verdicts can be compared and counted
 * across courts and categories.
 */
public enum VerdictResult {
	ALLOWED("Allowed"),
	PARTLY_ALLOWED("Partly allowed"),
	DISMISSED("Dismissed"),
	CONVICTED("Convicted"),
	ACQUITTED("Acquitted"),
	BAIL_GRANTED("Bail granted"),
	BAIL_REJECTED("Bail rejected"),
	DECREED("Suit decreed"),
	REMANDED("Remanded"),
	SETTLED("Settled / compromised"),
	WITHDRAWN("Withdrawn"),
	DISPOSED_OF("Disposed of"),
	PENDING("Pending"),
	OTHER("Other");

	private final String displayName;

	VerdictResult(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
