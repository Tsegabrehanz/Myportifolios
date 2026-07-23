import { UserRole } from './auth.model';

export interface UserSummary {
  id: number;
  email: string;
  role: UserRole;
  enabled: boolean;
  mustChangePassword: boolean;
  employeeId: number | null;
  employeeName: string | null;
}

export const ALL_ROLES: UserRole[] = ['SUPER_ADMIN', 'HR_ADMIN', 'MANAGER', 'EMPLOYEE', 'AUDITOR'];

export interface PasswordResetResponse {
  user: UserSummary;
  temporaryPassword: string;
}
