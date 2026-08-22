package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByAvailableTrueOrderByCategoryAscNameAsc();

    List<Product> findAllByOrderByCategoryAscNameAsc();
}
