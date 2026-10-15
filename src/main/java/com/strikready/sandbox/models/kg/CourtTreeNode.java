package com.strikready.sandbox.models.kg;

import java.util.ArrayList;
import java.util.List;

/**
 * A court with the subordinate forums whose appeals lie to it.
 */
public class CourtTreeNode {

	private final Court court;
	private final List<CourtTreeNode> subordinates = new ArrayList<>();

	public CourtTreeNode(Court court) {
		this.court = court;
	}

	public Court getCourt() {
		return court;
	}

	public List<CourtTreeNode> getSubordinates() {
		return subordinates;
	}
}
