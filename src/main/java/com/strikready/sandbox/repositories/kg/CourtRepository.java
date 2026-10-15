package com.strikready.sandbox.repositories.kg;

import java.util.List;

import com.strikready.sandbox.models.kg.Court;
import com.strikready.sandbox.models.kg.CourtLevel;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CourtRepository extends MongoRepository<Court, String> {

	Court findByCode(String code);

	List<Court> findByCodeIn(Iterable<String> codes);

	List<Court> findByLevel(CourtLevel level);

	List<Court> findByAppellateCourtCode(String appellateCourtCode);
}
