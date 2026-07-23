package com.eems.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ReportDtos {

    public record DepartmentHeadcount(
            String departmentName,
            long headcount
    ) {}

    /**
     * Standard HR analytics metrics: headcount, org distribution, tenure,
     * attrition, and leave utilization. Deliberately excludes any
     * protected-characteristic breakdowns (no demographic fields exist
     * on Employee in the first place) - these are organizational/
     * operational metrics only.
     */
    public record HrSummaryReport(
            Instant generatedAt,
            long totalActiveEmployees,
            long totalHeadcountAllStatuses,
            List<DepartmentHeadcount> headcountByDepartment,
            Map<String, Long> headcountByStatus,
            double averageTenureYears,
            long newHiresLast12Months,
            long offboardedLast12Months,
            double attritionRatePercent,
            Map<String, Long> leaveRequestsByStatus,
            Map<String, Long> leaveRequestsByType,
            long pendingLeaveApprovals
    ) {}
}
