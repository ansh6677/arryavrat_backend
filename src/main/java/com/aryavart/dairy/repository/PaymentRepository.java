package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    List<Payment> findByCustomerIdOrderByPaymentDateDesc(String customerId);

    /** Single-document $gte/$lte range (see DailyEntryRepository note). */
    @Query(value = "{ 'paymentDate': { '$gte': ?0, '$lte': ?1 } }",
           sort = "{ 'paymentDate': -1, 'createdAt': -1 }")
    List<Payment> findInRange(LocalDate from, LocalDate to);
}
