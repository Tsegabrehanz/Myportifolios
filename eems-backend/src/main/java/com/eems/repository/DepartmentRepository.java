package com.eems.repository;

import com.eems.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByParentDepartmentId(Long parentId);
    Optional<Department> findByNameIgnoreCase(String name);
}
