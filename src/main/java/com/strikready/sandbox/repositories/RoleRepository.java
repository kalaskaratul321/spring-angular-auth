package com.strikready.sandbox.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.strikready.sandbox.models.Role;

public interface RoleRepository extends MongoRepository<Role, String> {

	Role findByRole(String role);
}