package com.eems.repository;

import com.eems.entity.PasswordChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordChangeRequestRepository extends JpaRepository<PasswordChangeRequest, Long> {
    Optional<PasswordChangeRequest> findFirstByUserIdAndConsumedFalseOrderByCreatedAtDesc(Long userId);
}
