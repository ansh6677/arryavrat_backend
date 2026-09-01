package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.DailyEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyEntryRepository extends MongoRepository<DailyEntry, String> {

    List<DailyEntry> findByCustomerIdOrderByEntryDateAsc(String customerId);

    Optional<DailyEntry> findFirstByLinkedPaymentId(String linkedPaymentId);

    /** The just-saved old due, returned again when a duplicate submit replays the same requestId. */
    Optional<DailyEntry> findFirstByCustomerIdAndOldDueTrueAndForPeriodOrderByCreatedAtDesc(String customerId, String forPeriod);

    /**
     * Note: a derived "GreaterThanEqual...LessThanEqual" method on the SAME field
     * throws InvalidMongoDbApiUsageException, so the range is written as one $gte/$lte document.
     */
    @Query(value = "{ 'customerId': ?0, 'entryDate': { '$gte': ?1, '$lte': ?2 } }",
           sort = "{ 'entryDate': 1, 'createdAt': 1 }")
    List<DailyEntry> findForCustomerInRange(String customerId, LocalDate from, LocalDate to);

    @Query(value = "{ 'entryDate': { '$gte': ?0, '$lte': ?1 } }",
           sort = "{ 'entryDate': -1, 'createdAt': -1 }")
    List<DailyEntry> findInRange(LocalDate from, LocalDate to);
}
