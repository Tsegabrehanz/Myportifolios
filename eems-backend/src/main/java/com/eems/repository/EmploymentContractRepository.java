package com.eems.repository;

import com.eems.entity.EmploymentContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {
    List<EmploymentContract> findByEmployeeIdOrderByStartDateDesc(Long employeeId);
}
