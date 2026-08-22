package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.Expense;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends MongoRepository<Expense, String> {

    /** Single-document $gte/$lte range (see DailyEntryRepository note). */
    @Query(value = "{ 'expenseDate': { '$gte': ?0, '$lte': ?1 } }",
           sort = "{ 'expenseDate': -1, 'createdAt': -1 }")
    List<Expense> findInRange(LocalDate from, LocalDate to);
}
