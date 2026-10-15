package com.strikready.sandbox.repositories.kg.harvest;

import java.util.List;

import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HarvestSourceRepository extends MongoRepository<HarvestSource, String> {

	HarvestSource findByName(String name);

	List<HarvestSource> findByEnabledTrue();
}
