package com.eems.service;

import com.eems.dto.PowerBiDtos.DepartmentRow;
import com.eems.dto.PowerBiDtos.EmployeeRow;
import com.eems.dto.PowerBiDtos.LeaveRequestRow;
import com.eems.entity.Department;
import com.eems.entity.Employee;
import com.eems.entity.EmployeeStatus;
import com.eems.entity.LeaveRequest;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Produces the same three flat "tables" as db/powerbi-reporting-views.sql,
 * but over REST/JSON instead of a direct DB connection - useful for
 * Power BI Desktop's Web connector when you don't have (or don't want to
 * grant) direct PostgreSQL access, or when running against the H2 dev
 * profile where the SQL views don't apply.
 */
@Service
@RequiredArgsConstructor
public class PowerBiService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public List<EmployeeRow> employeeRows() {
        return employeeRepository.findAllWithRelations().stream()
                .map(this::toEmployeeRow)
                .toList();
    }

    public List<DepartmentRow> departmentRows() {
        List<Employee> allEmployees = employeeRepository.findAllWithRelations();
        return departmentRepository.findAll().stream()
                .map(dept -> {
                    long active = allEmployees.stream()
                            .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(dept.getId()))
                            .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE)
                            .count();
                    long total = allEmployees.stream()
                            .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(dept.getId()))
                            .count();
                    return new DepartmentRow(dept.getId(), dept.getName(), dept.getLocation(), active, total);
                })
                .toList();
    }

    public List<LeaveRequestRow> leaveRequestRows() {
        return leaveRequestRepository.findAllWithRelations().stream()
                .map(this::toLeaveRow)
                .toList();
    }

    private EmployeeRow toEmployeeRow(Employee e) {
        Department d = e.getDepartment();
        Employee manager = e.getManager();
        com.eems.entity.Position position = e.getPosition();
        return new EmployeeRow(
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                position != null ? position.getId() : null,
                position != null ? position.getTitle() : null,
                e.getStatus().name(),
                e.getHireDate(),
                e.getExitDate(),
                d != null ? d.getId() : null,
                d != null ? d.getName() : null,
                manager != null ? manager.getId() : null,
                manager != null ? manager.getFirstName() + " " + manager.getLastName() : null
        );
    }

    private LeaveRequestRow toLeaveRow(LeaveRequest l) {
        Employee employee = l.getEmployee();
        Department d = employee.getDepartment();
        Employee approver = l.getApprovedBy();
        long durationDays = ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1;

        return new LeaveRequestRow(
                l.getId(),
                l.getType().name(),
                l.getStatus().name(),
                l.getStartDate(),
                l.getEndDate(),
                durationDays,
                l.getCreatedAt(),
                l.getDecidedAt(),
                employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                d != null ? d.getId() : null,
                d != null ? d.getName() : null,
                approver != null ? approver.getId() : null,
                approver != null ? approver.getFirstName() + " " + approver.getLastName() : null
        );
    }
}
