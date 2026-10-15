package com.strikready.sandbox.models.kg;

import java.util.Locale;

/**
 * A provision relied on in a case, e.g. act "IPC", section "302".
 */
public class StatuteReference {

	private String act;
	private String section;

	public StatuteReference() {
	}

	public StatuteReference(String act, String section) {
		this.act = act;
		this.section = section;
	}

	/** Normalised STATUTE node identifier: "Cr.P.C." / "439" -> {@code CRPC:439}, "IPC" / "498-A" -> {@code IPC:498A}. */
	public String identifier() {
		String a = act == null ? "" : act.toUpperCase(Locale.ROOT).replace(".", "").replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("_?(1[6-9]|20)\\d{2}$", "").replaceAll("^_|_$", "");
		String s = section == null ? "" : section.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
		return s.isEmpty() ? a : a + ":" + s;
	}

	public String getAct() {
		return act;
	}

	public void setAct(String act) {
		this.act = act;
	}

	public String getSection() {
		return section;
	}

	public void setSection(String section) {
		this.section = section;
	}
}
