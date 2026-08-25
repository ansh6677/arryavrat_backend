package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.LoginEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoginEventRepository extends MongoRepository<LoginEvent, String> {

    List<LoginEvent> findTop12ByOrderByAtDesc();

    Optional<LoginEvent> findFirstBySideOrderByAtDesc(String side);

    /** Housekeeping — the feed only ever needs recent history. */
    void deleteByAtBefore(Instant cutoff);
}
