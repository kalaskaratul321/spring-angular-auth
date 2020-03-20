package com.strikready.sandbox.repositories;

import com.strikready.sandbox.models.Products;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Products, String> {
	
	@Override
    void delete(Products deleted);
}