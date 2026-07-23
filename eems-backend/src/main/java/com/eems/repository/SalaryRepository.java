package com.eems.repository;

import com.eems.entity.Salary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryRepository extends JpaRepository<Salary, Long> {
    List<Salary> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
