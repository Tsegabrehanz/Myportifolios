package com.eems.config;

import com.eems.entity.*;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.LeaveBalanceRepository;
import com.eems.repository.PositionRepository;
import com.eems.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Seeds a minimal working dataset in the "dev" and "docker" profiles
 * only, so the API and frontend can be exercised immediately without
 * manual setup. Also required for "docker": there's no public
 * registration endpoint, so without this the compose stack would start
 * with an empty database and no way to log in at all.
 * NEVER enable this in "prod" - it creates well-known logins.
 */
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        Department engineering = departmentRepository.save(
                Department.builder().name("Engineering").location("Frankfurt, DE").build());

        Position engineeringManagerPosition = positionRepository.save(
                Position.builder().title("Engineering Manager").grade("M2").salaryBand("B7").department(engineering).build());
        Position softwareEngineerPosition = positionRepository.save(
                Position.builder().title("Software Engineer").grade("P3").salaryBand("B5").department(engineering).build());

        // Super admin - change this password immediately in any non-throwaway environment.
        // Phone numbers below are fake placeholders so the SMS-confirmed
        // change-password flow can be exercised locally (the OTP prints
        // to the console via ConsoleSmsSender - see com.eems.sms).
        User adminUser = userRepository.save(User.builder()
                .email("admin@eems.local")
                .passwordHash(passwordEncoder.encode("ChangeMe123!"))
                .role(Role.SUPER_ADMIN)
                .phoneNumber("+15550100001")
                .build());

        // Manager
        User managerUser = userRepository.save(User.builder()
                .email("manager@eems.local")
                .passwordHash(passwordEncoder.encode("ChangeMe123!"))
                .role(Role.MANAGER)
                .phoneNumber("+15550100002")
                .build());
        Employee manager = employeeRepository.save(Employee.builder()
                .user(managerUser)
                .firstName("Maria")
                .lastName("Manager")
                .position(engineeringManagerPosition)
                .department(engineering)
                .hireDate(LocalDate.of(2022, 1, 10))
                .status(EmployeeStatus.ACTIVE)
                .build());

        // Regular employee reporting to the manager above
        User employeeUser = userRepository.save(User.builder()
                .email("employee@eems.local")
                .passwordHash(passwordEncoder.encode("ChangeMe123!"))
                .role(Role.EMPLOYEE)
                .phoneNumber("+15550100003")
                .build());
        Employee erik = employeeRepository.save(Employee.builder()
                .user(employeeUser)
                .firstName("Erik")
                .lastName("Employee")
                .position(softwareEngineerPosition)
                .department(engineering)
                .manager(manager)
                .hireDate(LocalDate.of(2023, 6, 1))
                .status(EmployeeStatus.ACTIVE)
                .build());

        // Starter leave balances for the current year, so the balance
        // calculator has something to show/enforce immediately.
        int currentYear = LocalDate.now().getYear();
        leaveBalanceRepository.save(LeaveBalance.builder().employee(manager).leaveType(LeaveType.ANNUAL).year(currentYear).allocatedDays(25).build());
        leaveBalanceRepository.save(LeaveBalance.builder().employee(manager).leaveType(LeaveType.SICK).year(currentYear).allocatedDays(10).build());
        leaveBalanceRepository.save(LeaveBalance.builder().employee(erik).leaveType(LeaveType.ANNUAL).year(currentYear).allocatedDays(20).build());
        leaveBalanceRepository.save(LeaveBalance.builder().employee(erik).leaveType(LeaveType.SICK).year(currentYear).allocatedDays(10).build());

        System.out.println("========================================================");
        System.out.println(" Dev seed data loaded. Sample logins (password: ChangeMe123!):");
        System.out.println("   admin@eems.local     (SUPER_ADMIN)");
        System.out.println("   manager@eems.local   (MANAGER)");
        System.out.println("   employee@eems.local  (EMPLOYEE, reports to manager@eems.local)");
        System.out.println(" All seeded accounts have placeholder phone numbers so you can");
        System.out.println(" try the Change Password (SMS confirmation) flow - the OTP code");
        System.out.println(" prints to this console instead of sending a real text.");
        System.out.println(" manager@eems.local and employee@eems.local both have starter");
        System.out.println(" leave balances (25/20 annual, 10 sick days) for the current year.");
        System.out.println("========================================================");
    }
}
