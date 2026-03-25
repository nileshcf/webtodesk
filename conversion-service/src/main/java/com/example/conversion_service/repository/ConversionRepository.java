package com.example.conversion_service.repository;

import com.example.conversion_service.entity.ConversionProject;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConversionRepository extends MongoRepository<ConversionProject, String> {
    List<ConversionProject> findByCreatedByOrderByCreatedAtDesc(String createdBy);
}
