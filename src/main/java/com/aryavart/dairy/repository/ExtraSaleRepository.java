package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.ExtraSale;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ExtraSaleRepository extends MongoRepository<ExtraSale, String> {

    /** Single-clause range query — two derived comparisons on one field break the parser. */
    @Query(value = "{ 'saleDate': { $gte: ?0, $lte: ?1 } }", sort = "{ 'saleDate': -1, 'createdAt': -1 }")
    List<ExtraSale> findInRange(LocalDate from, LocalDate to);
}
