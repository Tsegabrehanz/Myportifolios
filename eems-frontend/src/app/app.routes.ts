import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './shared/layout/shell.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { EmployeeListComponent } from './features/employees/employee-list.component';
import { EmployeeFormComponent } from './features/employees/employee-form.component';
import { EmployeeProfileComponent } from './features/employee-profile/employee-profile.component';
import { DepartmentListComponent } from './features/departments/department-list.component';
import { PositionListComponent } from './features/positions/position-list.component';
import { LeaveComponent } from './features/leave/leave.component';
import { ChangePasswordComponent } from './features/change-password/change-password.component';
import { AnalyticsComponent } from './features/analytics/analytics.component';
import { UserManagementComponent } from './features/admin/user-management.component';
import { LeaveBalanceManagementComponent } from './features/leave-balances/leave-balance-management.component';
import { AuditLogViewerComponent } from './features/audit-logs/audit-log-viewer.component';
import { JobPostingListComponent } from './features/job-postings/job-posting-list.component';
import { OrgChartComponent } from './features/org-chart/org-chart.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'employees', component: EmployeeListComponent },
      {
        path: 'employees/new',
        component: EmployeeFormComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN')]
      },
      { path: 'employees/:id', component: EmployeeProfileComponent },
      {
        path: 'employees/:id/edit',
        component: EmployeeFormComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN', 'MANAGER')]
      },
      {
        path: 'departments',
        component: DepartmentListComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN')]
      },
      {
        path: 'positions',
        component: PositionListComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN')]
      },
      { path: 'leave', component: LeaveComponent },
      {
        path: 'leave-balances',
        component: LeaveBalanceManagementComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN')]
      },
      { path: 'change-password', component: ChangePasswordComponent },
      {
        path: 'analytics',
        component: AnalyticsComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'HR_ADMIN', 'AUDITOR')]
      },
      {
        path: 'admin/users',
        component: UserManagementComponent,
        canActivate: [roleGuard('SUPER_ADMIN')]
      },
      {
        path: 'audit-logs',
        component: AuditLogViewerComponent,
        canActivate: [roleGuard('SUPER_ADMIN', 'AUDITOR')]
      },
      {
        path: 'job-postings',
        component: JobPostingListComponent
        // No roleGuard - any authenticated user can view (visibility-scoped server-side: HR/Admin/Auditor see everything, everyone else only OPEN + INTERNAL/BOTH postings). Create/edit/delete UI only shows for HR/Admin, and the backend re-enforces that regardless.
      },
      {
        path: 'org-chart',
        component: OrgChartComponent
        // No roleGuard - built entirely from GET /api/employees, which is already visibility-scoped server-side (a MANAGER only sees their own reports, HR/Admin/Auditor see everyone).
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
