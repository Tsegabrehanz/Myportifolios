package com.eems.controller;

import com.eems.dto.PowerBiDtos.DepartmentRow;
import com.eems.dto.PowerBiDtos.EmployeeRow;
import com.eems.dto.PowerBiDtos.LeaveRequestRow;
import com.eems.service.PowerBiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Flat, tabular JSON endpoints intended for Power BI Desktop's Web
 * connector (Get Data -> Web). For production/scheduled-refresh use in
 * Power BI Service, prefer the direct PostgreSQL connection instead - see
 * db/powerbi-reporting-views.sql and the "Power BI integration" section
 * of README.md, since a short-lived JWT bearer token (this API's auth
 * model) is awkward for Power BI Service's unattended scheduled refresh.
 *
 * These three endpoints mirror the three SQL views 1:1 so the data model
 * is identical either way you connect.
 */
@RestController
@RequestMapping("/api/powerbi")
@RequiredArgsConstructor
public class PowerBiController {

    private final PowerBiService powerBiService;

    @GetMapping("/employees")
    public List<EmployeeRow> employees() {
        return powerBiService.employeeRows();
    }

    @GetMapping("/departments")
    public List<DepartmentRow> departments() {
        return powerBiService.departmentRows();
    }

    @GetMapping("/leave-requests")
    public List<LeaveRequestRow> leaveRequests() {
        return powerBiService.leaveRequestRows();
    }
}
