import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Department } from '../../core/models/department.model';
import { DepartmentImportSummaryResponse } from '../../core/models/department-import.model';
import { DepartmentApiService } from '../../core/services/department-api.service';

@Component({
  selector: 'app-department-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './department-list.component.html',
  styleUrl: './department-list.component.scss'
})
export class DepartmentListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly departments = signal<Department[]>([]);
  readonly displayedColumns = ['name', 'location', 'actions'];

  readonly importing = signal(false);
  readonly exporting = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly importResult = signal<DepartmentImportSummaryResponse | null>(null);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly form = this.fb.group({
    name: ['', Validators.required],
    location: ['']
  });

  constructor(private departmentApi: DepartmentApiService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.departmentApi.list().subscribe((departments) => this.departments.set(departments));
  }

  create(): void {
    if (this.form.invalid) {
      return;
    }
    const { name, location } = this.form.getRawValue();
    this.departmentApi.create({ name: name!, location: location || undefined }).subscribe(() => {
      this.form.reset();
      this.load();
    });
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
    this.departmentApi.importFile(file).subscribe({
      next: (result) => {
        this.importing.set(false);
        this.importResult.set(result);
        this.load();
        if (result.failureCount === 0) {
          this.snackBar.open(`Imported successfully — ${result.successCount} department(s) created.`, 'Dismiss', { duration: 4000 });
        } else {
          this.snackBar.open(`Import finished with issues — ${result.successCount} of ${result.totalRows} rows created. See details below.`, 'Dismiss', { duration: 6000 });
        }
      },
      error: (err) => {
        this.importing.set(false);
        this.errorMessage.set(this.extractError(err, 'Import failed — nothing was created. Please try again.'));
      },
      complete: () => {
        input.value = '';
      }
    });
  }

  dismissImportResult(): void {
    this.importResult.set(null);
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.departmentApi.exportCsv().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'eems-departments.csv';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      complete: () => this.exporting.set(false)
    });
  }

  deleteDepartment(department: Department): void {
    if (!confirm(`Delete department "${department.name}"? This cannot be undone.`)) {
      return;
    }
    this.errorMessage.set(null);
    this.deletingId.set(department.id);
    this.departmentApi.delete(department.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.errorMessage.set(this.extractError(err, 'Could not delete this department.'));
      }
    });
  }

  private extractError(err: unknown, fallback = 'Something went wrong.'): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || fallback;
  }
}
