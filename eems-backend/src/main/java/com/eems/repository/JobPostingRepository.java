package com.eems.repository;

import com.eems.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /** Same N+1 reasoning as EmployeeRepository's fetch-joined methods - toResponse() touches department and position, both LAZY. */
    @Query("SELECT p FROM JobPosting p LEFT JOIN FETCH p.department LEFT JOIN FETCH p.position")
    List<JobPosting> findAllWithRelations();
}
