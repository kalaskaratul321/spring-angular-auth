package com.strikready.sandbox.services.kg.harvest;

import java.time.LocalDate;

/**
 * An inclusive date range to discover documents for. {@code start} and
 * {@code end} are null for undated sources (read in full each run).
 */
public class HarvestWindow {

	private final LocalDate start;
	private final LocalDate end;

	public HarvestWindow(LocalDate start, LocalDate end) {
		this.start = start;
		this.end = end;
	}

	public static HarvestWindow undated() {
		return new HarvestWindow(null, null);
	}

	public LocalDate getStart() {
		return start;
	}

	public LocalDate getEnd() {
		return end;
	}

	public boolean isDated() {
		return start != null;
	}

	@Override
	public String toString() {
		return isDated() ? start + ".." + end : "undated";
	}
}
