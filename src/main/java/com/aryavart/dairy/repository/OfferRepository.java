package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.Offer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends MongoRepository<Offer, String> {

    /** Codes are stored upper-case, so look them up upper-case too. */
    Optional<Offer> findByCode(String code);

    boolean existsByCode(String code);

    /** Newest first — the management table's natural order. */
    List<Offer> findAllByOrderByCreatedAtDesc();

    List<Offer> findByActiveTrueOrderByCreatedAtDesc();
}
