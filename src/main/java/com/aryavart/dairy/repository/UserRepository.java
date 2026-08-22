package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByRoleOrderByNameAsc(String role);

    List<User> findByRoleInOrderByNameAsc(Collection<String> roles);

    long countByRole(String role);
}
