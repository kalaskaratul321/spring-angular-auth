package com.strikready.sandbox.repositories.kg.harvest;

import com.strikready.sandbox.models.kg.harvest.HarvestTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HarvestTaskRepository extends MongoRepository<HarvestTask, String> {

	Page<HarvestTask> findByStatus(HarvestTask.Status status, Pageable pageable);

	Page<HarvestTask> findBySourceIdAndStatus(String sourceId, HarvestTask.Status status, Pageable pageable);

	long countBySourceIdAndStatus(String sourceId, HarvestTask.Status status);
}
