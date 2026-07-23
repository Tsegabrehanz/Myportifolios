import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ChartComponent } from '../../shared/chart/chart.component';
import { ReportApiService } from '../../core/services/report-api.service';
import { HrSummaryReport } from '../../core/models/report.model';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTableModule,
    ChartComponent
  ],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss'
})
export class AnalyticsComponent implements OnInit {
  private readonly api = inject(ReportApiService);
  private readonly authService = inject(AuthService);

  readonly report = signal<HrSummaryReport | null>(null);
  readonly loading = signal(true);
  readonly exporting = signal<'pdf' | 'xlsx' | null>(null);
  readonly tokenCopied = signal(false);

  // Client-side only - the backend already returns every department's
  // headcount in one response, so filtering here just changes what's
  // displayed, not what's fetched. "All" (empty string) shows everything.
  readonly departmentFilter = signal<string>('');
  readonly displayedTableColumns = ['department', 'headcount', 'share'];

  readonly powerBiEndpoints = [
    { name: 'Employees', url: `${environment.apiUrl}/powerbi/employees` },
    { name: 'Departments', url: `${environment.apiUrl}/powerbi/departments` },
    { name: 'Leave Requests', url: `${environment.apiUrl}/powerbi/leave-requests` }
  ];

  ngOnInit(): void {
    this.api.getHrSummary().subscribe((report) => {
      this.report.set(report);
      this.loading.set(false);
    });
  }

  /**
   * Every one of these was a plain getter before - each one built a
   * brand-new array (.map()/.filter()/Object.keys()) on every single
   * Angular change-detection check, even when nothing had actually
   * changed. Since <app-chart> and <mat-table> both compare inputs by
   * reference, a new array every check meant ngOnChanges fired every
   * check, which rebuilds the Chart.js instance, which schedules a
   * requestAnimationFrame that Angular's zone intercepts and triggers
   * ANOTHER change-detection cycle from - a genuine infinite render
   * loop that pegs the CPU and freezes the tab. computed() caches its
   * result and only recomputes when a signal it actually reads
   * (report/departmentFilter) changes, so the same underlying data
   * yields the same array reference across checks, breaking the loop.
   */
  private readonly filteredDepartments = computed(() => {
    const all = this.report()?.headcountByDepartment ?? [];
    return this.departmentFilter() ? all.filter((d) => d.departmentName === this.departmentFilter()) : all;
  });

  readonly departmentLabels = computed(() => this.filteredDepartments().map((d) => d.departmentName));
  readonly departmentData = computed(() => this.filteredDepartments().map((d) => d.headcount));

  /** For the table - includes each department's share of total headcount, same style as a "Participation by Department" breakdown. */
  readonly departmentTableRows = computed(() => {
    const totalAcrossAllDepartments = (this.report()?.headcountByDepartment ?? []).reduce((sum, d) => sum + d.headcount, 0);
    return this.filteredDepartments().map((d) => ({
      departmentName: d.departmentName,
      headcount: d.headcount,
      sharePercent: totalAcrossAllDepartments > 0 ? Math.round((d.headcount / totalAcrossAllDepartments) * 1000) / 10 : 0
    }));
  });

  readonly statusLabels = computed(() => Object.keys(this.report()?.headcountByStatus ?? {}));
  readonly statusData = computed(() => Object.values(this.report()?.headcountByStatus ?? {}));

  readonly leaveStatusLabels = computed(() => Object.keys(this.report()?.leaveRequestsByStatus ?? {}));
  readonly leaveStatusData = computed(() => Object.values(this.report()?.leaveRequestsByStatus ?? {}));

  readonly leaveTypeLabels = computed(() => Object.keys(this.report()?.leaveRequestsByType ?? {}));
  readonly leaveTypeData = computed(() => Object.values(this.report()?.leaveRequestsByType ?? {}));

  exportPdf(): void {
    this.exporting.set('pdf');
    this.api.downloadPdf().subscribe({
      next: (blob) => this.downloadBlob(blob, 'eems-hr-summary.pdf'),
      complete: () => this.exporting.set(null)
    });
  }

  exportExcel(): void {
    this.exporting.set('xlsx');
    this.api.downloadExcel().subscribe({
      next: (blob) => this.downloadBlob(blob, 'eems-hr-summary.xlsx'),
      complete: () => this.exporting.set(null)
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  copyAccessToken(): void {
    const token = this.authService.getAccessToken();
    if (!token) {
      return;
    }
    navigator.clipboard.writeText(token).then(() => {
      this.tokenCopied.set(true);
      setTimeout(() => this.tokenCopied.set(false), 2000);
    });
  }

  copyUrl(url: string): void {
    navigator.clipboard.writeText(url);
  }
}
