package com.strikready.sandbox.repositories;

import com.strikready.sandbox.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

	User findByEmail(String email);
}