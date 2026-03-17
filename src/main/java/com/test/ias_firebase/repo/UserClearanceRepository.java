package com.test.ias_firebase.repo;

import com.test.ias_firebase.model.UserClearance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserClearanceRepository extends JpaRepository<UserClearance, String> {
    Optional<UserClearance> findByEmailIgnoreCase(String email);
}
