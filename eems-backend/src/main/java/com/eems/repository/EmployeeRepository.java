package com.eems.repository;

import com.eems.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByManagerId(Long managerId);
    List<Employee> findByDepartmentId(Long departmentId);
    Optional<Employee> findByUserId(Long userId);
    Optional<Employee> findByUserEmail(String email);
    long countByDepartmentId(Long departmentId);
    long countByPositionId(Long positionId);
    long countByManagerId(Long managerId);

    /**
     * Eagerly fetches each employee's department/position/manager/user in
     * the same query via LEFT JOIN FETCH, instead of those (all
     * @ManyToOne/@OneToOne LAZY relationships) triggering separate
     * SELECTs per employee the moment anything calls
     * employee.getDepartment()/getPosition()/getManager()/getUser() - a
     * classic N+1 query problem that gets worse linearly with headcount.
     * `user` was added when EmployeeResponse started including email
     * (toResponse() reads e.getUser().getEmail()) - the same care that
     * went into fixing the original N+1 has to be repeated every time a
     * new lazy relationship gets touched in a per-row mapping like this
     * one, or the bug just comes back. Use this anywhere you need to map
     * many employees to a response/report at once
     * (EmployeeService.listVisibleTo, ReportService, PowerBiService) -
     * not needed for endpoints that only touch a handful of employees at
     * a time, where the N+1 cost is negligible.
     */
    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.department LEFT JOIN FETCH e.position LEFT JOIN FETCH e.manager LEFT JOIN FETCH e.user")
    List<Employee> findAllWithRelations();

    /** Same fetch-join reasoning as findAllWithRelations(), scoped to one manager's direct reports. */
    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.department LEFT JOIN FETCH e.position LEFT JOIN FETCH e.manager LEFT JOIN FETCH e.user WHERE e.manager.id = :managerId")
    List<Employee> findByManagerIdWithRelations(Long managerId);
}
