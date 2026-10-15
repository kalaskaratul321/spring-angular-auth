package com.strikready.sandbox.repositories.kg;

import java.util.List;

import com.strikready.sandbox.models.kg.KgEdge;
import com.strikready.sandbox.models.kg.RelationType;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KgEdgeRepository extends MongoRepository<KgEdge, String> {

	List<KgEdge> findBySourceIn(Iterable<String> sources);

	List<KgEdge> findByTargetIn(Iterable<String> targets);

	List<KgEdge> findByTargetAndRelation(String target, RelationType relation);

	List<KgEdge> findByTarget(String target);

	boolean existsBySourceAndRelationAndTarget(String source, RelationType relation, String target);

	void deleteBySource(String source);
}
