export interface DepartmentHeadcount {
  departmentName: string;
  headcount: number;
}

export interface HrSummaryReport {
  generatedAt: string;
  totalActiveEmployees: number;
  totalHeadcountAllStatuses: number;
  headcountByDepartment: DepartmentHeadcount[];
  headcountByStatus: Record<string, number>;
  averageTenureYears: number;
  newHiresLast12Months: number;
  offboardedLast12Months: number;
  attritionRatePercent: number;
  leaveRequestsByStatus: Record<string, number>;
  leaveRequestsByType: Record<string, number>;
  pendingLeaveApprovals: number;
}
