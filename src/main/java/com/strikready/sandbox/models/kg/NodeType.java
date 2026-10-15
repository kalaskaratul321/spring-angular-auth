package com.strikready.sandbox.models.kg;

/**
 * Kinds of vertices in the case knowledge graph. A node is addressed by a key
 * of the form {@code TYPE:identifier}, e.g. {@code COURT:BHC-MUM} or
 * {@code STATUTE:IPC:302}.
 */
public enum NodeType {
	CASE, COURT, CATEGORY, STATUTE, JUDGE, PARTY;

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
}
