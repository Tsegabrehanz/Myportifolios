import { LeaveType } from './leave.model';

export interface LeaveBalance {
  employeeId: number;
  employeeName: string;
  leaveType: LeaveType;
  year: number;
  allocatedDays: number;
  usedDays: number;
  pendingDays: number;
  availableDays: number;
  tracked: boolean;
}

export interface UpsertLeaveBalanceRequest {
  employeeId: number;
  leaveType: LeaveType;
  year: number;
  allocatedDays: number;
}

export interface BulkAllocateRequest {
  leaveType: LeaveType;
  year: number;
  allocatedDays: number;
  departmentId?: number;
}

export interface BulkAllocateResponse {
  employeeCount: number;
  balances: LeaveBalance[];
}

export interface CarryOverRequest {
  leaveType: LeaveType;
  fromYear: number;
  toYear: number;
  maxCarryOverDays?: number;
}

export interface CarryOverResult {
  employeeId: number;
  employeeName: string;
  carriedOverDays: number;
  newToYearAllocation: number;
}

export interface CarryOverResponse {
  employeeCount: number;
  results: CarryOverResult[];
}

/** Only these three are balance-tracked - UNPAID and OTHER have no cap. */
export const TRACKED_LEAVE_TYPES: LeaveType[] = ['ANNUAL', 'SICK', 'PARENTAL'];
