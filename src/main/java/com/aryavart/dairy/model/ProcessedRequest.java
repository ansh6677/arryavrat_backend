package com.aryavart.dairy.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One save-request id, remembered briefly so an accidental duplicate submit
 * (double tap, network retry) is answered without writing anything twice.
 * The unique _id is the client-generated requestId; the first insert wins.
 */
@Document("processed_requests")
public class ProcessedRequest {

    @Id
    private String id;

    /** Mongo TTL — old ids clean themselves up after a day. */
    @Indexed(expireAfter = "24h")
    private Instant createdAt = Instant.now();

    public ProcessedRequest() {
    }

    public ProcessedRequest(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
