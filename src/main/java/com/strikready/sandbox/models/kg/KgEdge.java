package com.strikready.sandbox.models.kg;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A directed edge between two graph nodes, stored in its own collection so the
 * graph can be traversed in either direction (and with $graphLookup).
 */
@Document(collection = "kg_edges")
@CompoundIndexes({
		@CompoundIndex(name = "edge_unique_idx", def = "{'source': 1, 'relation': 1, 'target': 1}", unique = true),
		@CompoundIndex(name = "edge_target_idx", def = "{'target': 1, 'relation': 1}") })
public class KgEdge {

	@Id
	private String id;
	private String source;
	private RelationType relation;
	private String target;
	/** Category of the source case, so category subgraphs can be filtered without joins. */
	private CaseCategory category;

	public KgEdge() {
	}

	public KgEdge(String source, RelationType relation, String target, CaseCategory category) {
		this.source = source;
		this.relation = relation;
		this.target = target;
		this.category = category;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public RelationType getRelation() {
		return relation;
	}

	public void setRelation(RelationType relation) {
		this.relation = relation;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public CaseCategory getCategory() {
		return category;
	}

	public void setCategory(CaseCategory category) {
		this.category = category;
	}
}
