package com.strikready.sandbox.repositories;

import com.strikready.sandbox.models.Organization;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizationRepository extends MongoRepository<Organization, String> {

	Organization findByDomain(String domain);
}