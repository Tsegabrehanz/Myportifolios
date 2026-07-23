import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Position } from '../../core/models/position.model';
import { Department } from '../../core/models/department.model';
import { PositionImportSummaryResponse } from '../../core/models/position-import.model';
import { PositionApiService } from '../../core/services/position-api.service';
import { DepartmentApiService } from '../../core/services/department-api.service';

@Component({
  selector: 'app-position-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule
  ],
  templateUrl: './position-list.component.html',
  styleUrl: './position-list.component.scss'
})
export class PositionListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly positions = signal<Position[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly displayedColumns = ['title', 'grade', 'salaryBand', 'department', 'actions'];

  readonly importing = signal(false);
  readonly exporting = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly importResult = signal<PositionImportSummaryResponse | null>(null);

  // null = create mode; set = editing that position's id. Title can't
  // be changed once created (see PositionService.update javadoc - it's
  // what CSV import and other lookups match against), so the title
  // field is disabled while editing.
  readonly editingId = signal<number | null>(null);
  readonly expandedId = signal<number | null>(null);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  readonly form = this.fb.group({
    title: ['', Validators.required],
    grade: [''],
    salaryBand: [''],
    jobDescription: [''],
    departmentId: [null as number | null]
  });

  constructor(private positionApi: PositionApiService, private departmentApi: DepartmentApiService) {}

  ngOnInit(): void {
    this.load();
    this.departmentApi.list().subscribe((departments) => this.departments.set(departments));
  }

  load(): void {
    this.positionApi.list().subscribe((positions) => this.positions.set(positions));
  }

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { title, grade, salaryBand, jobDescription, departmentId } = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);

    const editingId = this.editingId();
    if (editingId !== null) {
      this.positionApi
        .update(editingId, {
          grade: grade || undefined,
          salaryBand: salaryBand || undefined,
          jobDescription: jobDescription || undefined,
          departmentId: departmentId || undefined
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.cancelEdit();
            this.load();
          },
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err, 'Could not update this position.'));
          }
        });
    } else {
      this.positionApi
        .create({
          title: title!,
          grade: grade || undefined,
          salaryBand: salaryBand || undefined,
          jobDescription: jobDescription || undefined,
          departmentId: departmentId || undefined
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.form.reset();
            this.load();
          },
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err, 'Could not create this position.'));
          }
        });
    }
  }

  startEdit(position: Position): void {
    this.editingId.set(position.id);
    this.form.patchValue({
      title: position.title,
      grade: position.grade ?? '',
      salaryBand: position.salaryBand ?? '',
      jobDescription: position.jobDescription ?? '',
      departmentId: position.departmentId
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset();
  }

  toggleExpand(position: Position): void {
    this.expandedId.set(this.expandedId() === position.id ? null : position.id);
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
    this.positionApi.importFile(file).subscribe({
      next: (result) => {
        this.importing.set(false);
        this.importResult.set(result);
        this.load();
        if (result.failureCount === 0) {
          this.snackBar.open(`Imported successfully — ${result.successCount} position(s) created.`, 'Dismiss', { duration: 4000 });
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
    this.positionApi.exportCsv().subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'eems-positions.csv';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      complete: () => this.exporting.set(false)
    });
  }

  deletePosition(position: Position): void {
    if (!confirm(`Delete position "${position.title}"? This cannot be undone.`)) {
      return;
    }
    this.errorMessage.set(null);
    this.deletingId.set(position.id);
    this.positionApi.delete(position.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.errorMessage.set(this.extractError(err, 'Could not delete this position.'));
      }
    });
  }

  private extractError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || fallback;
  }
}
