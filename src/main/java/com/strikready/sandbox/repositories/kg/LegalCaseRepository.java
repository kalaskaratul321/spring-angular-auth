package com.strikready.sandbox.repositories.kg;

import java.util.List;

import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.CourtLevel;
import com.strikready.sandbox.models.kg.LegalCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LegalCaseRepository extends MongoRepository<LegalCase, String> {

	LegalCase findByCnr(String cnr);

	LegalCase findBySourceUrl(String url);

	boolean existsBySourceUrl(String url);

	LegalCase findFirstBySourceSha256(String sha256);

	Page<LegalCase> findByCategory(CaseCategory category, Pageable pageable);

	Page<LegalCase> findByCategoryAndCourtLevel(CaseCategory category, CourtLevel courtLevel, Pageable pageable);

	List<LegalCase> findByAppealFromCaseId(String appealFromCaseId);

	List<LegalCase> findByIdIn(Iterable<String> ids);

	List<LegalCase> findByCitationKeysIn(Iterable<String> citationKeys);
}
