import { Component, ElementRef, OnInit, ViewChild, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Employee } from '../../core/models/employee.model';
import { ImportSummaryResponse } from '../../core/models/employee-import.model';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatTableModule,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSnackBarModule
  ],
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss'
})
export class EmployeeListComponent implements OnInit {
  readonly employees = signal<Employee[]>([]);

  // Client-side, same reasoning as pagination below - the full list is
  // already fetched in one call, so filtering here is simpler than a
  // server-side search endpoint purely for this. Matches against name,
  // email, and employeeCode (case-insensitive substring).
  readonly searchTerm = signal('');
  readonly filteredEmployees = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) {
      return this.employees();
    }
    return this.employees().filter((e) => {
      const fullName = `${e.firstName} ${e.lastName}`.toLowerCase();
      return (
        fullName.includes(term) ||
        (e.email ?? '').toLowerCase().includes(term) ||
        (e.employeeCode ?? '').toLowerCase().includes(term)
      );
    });
  });
  readonly displayedColumns = ['employeeCode', 'name', 'position', 'department', 'manager', 'status', 'hireDate', 'actions'];

  // Client-side pagination - the full list is already fetched in one
  // call (there's no server-side paging endpoint for employees), so
  // slicing it here is simpler than adding one purely for this.
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);
  readonly pagedEmployees = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.filteredEmployees().slice(start, start + this.pageSize());
  });

  readonly importing = signal(false);
  readonly exporting = signal(false);
  readonly importResult = signal<ImportSummaryResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  constructor(private employeeApi: EmployeeApiService, public authService: AuthService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.employeeApi.list().subscribe((employees) => {
      this.employees.set(employees);
      this.pageIndex.set(0); // avoid landing on an out-of-range page after a refresh shrinks the list
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  onSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.pageIndex.set(0); // avoid landing on an out-of-range page after the result count shrinks
  }

  get canImport(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN');
  }

  get canCreate(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN');
  }

  get canEdit(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'MANAGER');
  }

  triggerFilePicker(): void {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.importing.set(true);
    this.importResult.set(null);
    this.errorMessage.set(null);
    this.employeeApi.importFile(file).subscribe({
      next: (result) => {
        this.importing.set(false);
        this.importResult.set(result);
        this.load();
        if (result.failureCount === 0) {
          this.snackBar.open(`Imported successfully — ${result.successCount} employee(s) created.`, 'Dismiss', { duration: 4000 });
        } else {
          this.snackBar.open(`Import finished with issues — ${result.successCount} of ${result.totalRows} rows created. See details below.`, 'Dismiss', { duration: 6000 });
        }
      },
      error: (err) => {
        this.importing.set(false);
        this.errorMessage.set(this.extractError(err, 'Import failed — nothing was created. Please try again.'));
      },
      complete: () => {
        input.value = ''; // allow re-selecting the same file name
      }
    });
  }

  dismissImportResult(): void {
    this.importResult.set(null);
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.employeeApi.exportCsv().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'eems-employees.csv';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      complete: () => this.exporting.set(false)
    });
  }

  private extractError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || fallback;
  }
}
