package com.eems.repository;

import com.eems.entity.EmployeeAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeAddressRepository extends JpaRepository<EmployeeAddress, Long> {
    Optional<EmployeeAddress> findByEmployeeId(Long employeeId);
}
