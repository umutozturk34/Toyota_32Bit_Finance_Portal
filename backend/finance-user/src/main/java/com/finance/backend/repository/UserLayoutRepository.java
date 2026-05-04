package com.finance.backend.repository;

import com.finance.backend.model.UserLayout;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLayoutRepository extends MongoRepository<UserLayout, String> {
}
