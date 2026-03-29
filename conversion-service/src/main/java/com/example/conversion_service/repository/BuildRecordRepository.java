package com.example.conversion_service.repository;

import com.example.conversion_service.entity.BuildRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BuildRecordRepository extends MongoRepository<BuildRecord, String> {

    List<BuildRecord> findByProjectIdOrderByStartedAtDesc(String projectId);

    List<BuildRecord> findByUserEmailOrderByStartedAtDesc(String userEmail);

    long countByProjectIdAndResult(String projectId, String result);
}
