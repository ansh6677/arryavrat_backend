package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.ProcessedRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedRequestRepository extends MongoRepository<ProcessedRequest, String> {
}
