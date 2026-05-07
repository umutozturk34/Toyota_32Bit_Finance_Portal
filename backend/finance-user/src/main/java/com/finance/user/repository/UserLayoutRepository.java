package com.finance.user.repository;

import com.finance.user.model.UserLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLayoutRepository extends JpaRepository<UserLayout, String> {
}
