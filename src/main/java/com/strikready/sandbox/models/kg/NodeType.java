package com.strikready.sandbox.models.kg;

/**
 * Kinds of vertices in the case knowledge graph. A node is addressed by a key
 * of the form {@code TYPE:identifier}, e.g. {@code COURT:BHC-MUM} or
 * {@code STATUTE:IPC:302}.
 *
 * ISSUE and CITATION nodes are deliberately shared between cases: two cases
 * deciding the same issue, or relying on the same reported judgment, meet there.
 */
public enum NodeType {
	CASE, COURT, CATEGORY, STATUTE, JUDGE, PARTY, ISSUE, VERDICT, CITATION;

	public String key(String identifier) {
		return name() + ":" + identifier;
	}

	public static NodeType ofKey(String key) {
		int idx = key.indexOf(':');
		if (idx < 0) {
			throw new IllegalArgumentException("Invalid node key: " + key);
		}
		return NodeType.valueOf(key.substring(0, idx));
	}

	public static String identifierOf(String key) {
		return key.substring(key.indexOf(':') + 1);
	}

	/** Lower-case, underscore-separated form: "(2019) 5 SCC 123" -> {@code 2019_5_scc_123}. */
	public static String slug(String text) {
		if (text == null) {
			return "";
		}
		return text.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}
}
