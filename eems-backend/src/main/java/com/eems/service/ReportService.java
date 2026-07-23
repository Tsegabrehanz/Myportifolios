package com.eems.service;

import com.eems.dto.ReportDtos.DepartmentHeadcount;
import com.eems.dto.ReportDtos.HrSummaryReport;
import com.eems.entity.Employee;
import com.eems.entity.EmployeeStatus;
import com.eems.entity.LeaveRequest;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public HrSummaryReport generateHrSummary() {
        // findAllWithRelations(), not findAll() - avoids one lazy-load
        // query per employee when this code reads e.getDepartment().getName()
        // below (a real N+1 query problem that scales linearly with headcount
        // and is the main reason this report got noticeably slower once real
        // employee counts grew past a couple dozen).
        List<Employee> allEmployees = employeeRepository.findAllWithRelations();
        List<LeaveRequest> allLeaveRequests = leaveRequestRepository.findAll();

        LocalDate now = LocalDate.now();
        LocalDate twelveMonthsAgo = now.minusMonths(12);

        long activeCount = allEmployees.stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                .count();

        Map<String, Long> headcountByStatus = allEmployees.stream()
                .collect(Collectors.groupingBy(e -> e.getStatus().name(), LinkedHashMap::new, Collectors.counting()));

        List<DepartmentHeadcount> byDepartment = allEmployees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.groupingBy(e -> e.getDepartment().getName(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new DepartmentHeadcount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DepartmentHeadcount::departmentName))
                .toList();

        double averageTenureYears = allEmployees.stream()
                .filter(e -> e.getHireDate() != null)
                .mapToDouble(e -> {
                    LocalDate end = e.getExitDate() != null ? e.getExitDate() : now;
                    return ChronoUnit.DAYS.between(e.getHireDate(), end) / 365.25;
                })
                .average()
                .orElse(0.0);

        long newHiresLast12Months = allEmployees.stream()
                .filter(e -> e.getHireDate() != null && !e.getHireDate().isBefore(twelveMonthsAgo))
                .count();

        long offboardedLast12Months = allEmployees.stream()
                .filter(e -> e.getStatus() == EmployeeStatus.OFFBOARDED
                        && e.getExitDate() != null
                        && !e.getExitDate().isBefore(twelveMonthsAgo))
                .count();

        // Simple attrition rate: offboarded in the period / average headcount
        // over the same period, using (start + end)/2 as the denominator -
        // a common, easy-to-explain approximation for a demo dashboard.
        long headcountNow = allEmployees.size();
        long headcountStartOfPeriod = headcountNow - newHiresLast12Months + offboardedLast12Months;
        double averageHeadcount = (headcountNow + headcountStartOfPeriod) / 2.0;
        double attritionRatePercent = averageHeadcount > 0
                ? (offboardedLast12Months / averageHeadcount) * 100.0
                : 0.0;

        Map<String, Long> leaveByStatus = allLeaveRequests.stream()
                .collect(Collectors.groupingBy(l -> l.getStatus().name(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> leaveByType = allLeaveRequests.stream()
                .collect(Collectors.groupingBy(l -> l.getType().name(), LinkedHashMap::new, Collectors.counting()));

        long pendingApprovals = allLeaveRequests.stream()
                .filter(l -> l.getStatus() == com.eems.entity.LeaveStatus.PENDING)
                .count();

        return new HrSummaryReport(
                Instant.now(),
                activeCount,
                allEmployees.size(),
                byDepartment,
                headcountByStatus,
                Math.round(averageTenureYears * 10.0) / 10.0,
                newHiresLast12Months,
                offboardedLast12Months,
                Math.round(attritionRatePercent * 10.0) / 10.0,
                leaveByStatus,
                leaveByType,
                pendingApprovals
        );
    }
}
