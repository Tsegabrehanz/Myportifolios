package com.eems.repository;

import com.eems.entity.LeaveBalance;
import com.eems.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeAndYear(Long employeeId, LeaveType leaveType, int year);
    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, int year);
    List<LeaveBalance> findByYear(int year);
}
