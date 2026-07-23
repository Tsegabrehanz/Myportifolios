package com.eems.repository;

import com.eems.entity.LeaveRequest;
import com.eems.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeId(Long employeeId);
    List<LeaveRequest> findByEmployeeManagerIdAndStatus(Long managerId, LeaveStatus status);
    List<LeaveRequest> findByStatus(LeaveStatus status);

    /**
     * Same N+1 reasoning as EmployeeRepository's fetch-joined methods,
     * but two lazy hops deep here: mapping a LeaveRequest to a flat row
     * (see PowerBiService.toLeaveRow) touches l.getEmployee() (LAZY),
     * then employee.getDepartment() (also LAZY), plus l.getApprovedBy()
     * (LAZY) - without this, that's up to 3 extra queries per leave
     * request rather than per employee, on top of the employee-level N+1.
     */
    @Query("SELECT l FROM LeaveRequest l LEFT JOIN FETCH l.employee e LEFT JOIN FETCH e.department LEFT JOIN FETCH l.approvedBy")
    List<LeaveRequest> findAllWithRelations();
}
