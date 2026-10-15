package com.strikready.sandbox.models.kg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A subgraph in a shape front-end graph libraries (vis-network, cytoscape,
 * ngx-graph) consume directly: a node list and an edge list.
 */
public class GraphView {

	private final List<Node> nodes = new ArrayList<>();
	private final List<Edge> edges = new ArrayList<>();

	public List<Node> getNodes() {
		return nodes;
	}

	public List<Edge> getEdges() {
		return edges;
	}

	public static class Node {
		private String id;
		private NodeType type;
		private String label;
		private CaseCategory category;
		private Map<String, Object> properties = new LinkedHashMap<>();

		public Node() {
		}

		public Node(String id, NodeType type, String label) {
			this.id = id;
			this.type = type;
			this.label = label;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public NodeType getType() {
			return type;
		}

		public void setType(NodeType type) {
			this.type = type;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public CaseCategory getCategory() {
			return category;
		}

		public void setCategory(CaseCategory category) {
			this.category = category;
		}

		public Map<String, Object> getProperties() {
			return properties;
		}

		public void setProperties(Map<String, Object> properties) {
			this.properties = properties;
		}
	}

	public static class Edge {
		private String source;
		private String target;
		private RelationType relation;

		public Edge() {
		}

		public Edge(String source, String target, RelationType relation) {
			this.source = source;
			this.target = target;
			this.relation = relation;
		}

		public String getSource() {
			return source;
		}

		public String getTarget() {
			return target;
		}

		public RelationType getRelation() {
			return relation;
		}
	}
}
