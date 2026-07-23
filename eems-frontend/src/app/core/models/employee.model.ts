export type EmployeeStatus = 'ONBOARDING' | 'ACTIVE' | 'ON_LEAVE' | 'OFFBOARDING' | 'OFFBOARDED';

export interface Employee {
  id: number;
  employeeCode: string | null;
  firstName: string;
  lastName: string;
  email: string | null;
  positionId: number | null;
  positionTitle: string | null;
  departmentId: number | null;
  departmentName: string | null;
  managerId: number | null;
  managerName: string | null;
  hireDate: string;
  exitDate: string | null;
  status: EmployeeStatus;
}

export interface CreateEmployeeRequest {
  firstName: string;
  lastName: string;
  nationalId?: string;
  positionId?: number;
  departmentId?: number;
  managerId?: number;
  hireDate: string;
  email?: string; // optional - auto-generated as firstname.lastname@eems.local if blank
  initialPassword?: string; // optional - a secure temporary password is generated server-side if blank
}

export interface UpdateEmployeeRequest {
  firstName?: string;
  lastName?: string;
  positionId?: number;
  departmentId?: number;
  managerId?: number;
  status?: EmployeeStatus;
}

/**
 * generatedEmail/generatedTemporaryPassword are only present when the
 * backend generated them (both null if the caller supplied their own).
 * This is the one and only response that will ever contain the
 * plaintext temporary password - it can't be retrieved again.
 */
export interface CreateEmployeeResponse {
  employee: Employee;
  generatedEmail: string | null;
  generatedTemporaryPassword: string | null;
}
