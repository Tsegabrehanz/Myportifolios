import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { EmployeeAddressApiService } from '../../core/services/employee-address-api.service';
import { EmergencyContactApiService } from '../../core/services/emergency-contact-api.service';
import { EmployeeDocumentApiService } from '../../core/services/employee-document-api.service';
import { OfferLetterApiService } from '../../core/services/offer-letter-api.service';
import { EmployeePhotoApiService } from '../../core/services/employee-photo-api.service';
import { AuthService } from '../../core/services/auth.service';
import { Employee } from '../../core/models/employee.model';
import { EmployeeAddress } from '../../core/models/employee-address.model';
import { EmergencyContact } from '../../core/models/emergency-contact.model';
import { EmployeeDocument } from '../../core/models/employee-document.model';

@Component({
  selector: 'app-employee-profile',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './employee-profile.component.html',
  styleUrl: './employee-profile.component.scss'
})
export class EmployeeProfileComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly employeeApi = inject(EmployeeApiService);
  private readonly addressApi = inject(EmployeeAddressApiService);
  private readonly contactApi = inject(EmergencyContactApiService);
  private readonly documentApi = inject(EmployeeDocumentApiService);
  private readonly offerLetterApi = inject(OfferLetterApiService);
  private readonly photoApi = inject(EmployeePhotoApiService);
  public readonly authService = inject(AuthService);

  private employeeId!: number;

  readonly loading = signal(true);
  readonly employee = signal<Employee | null>(null);
  readonly contacts = signal<EmergencyContact[]>([]);
  readonly documents = signal<EmployeeDocument[]>([]);
  readonly generatingOfferLetter = signal(false);
  readonly photoUrl = signal<string | null>(null);
  readonly uploadingPhoto = signal(false);

  @ViewChild('photoInput') photoInput!: ElementRef<HTMLInputElement>;

  readonly editingAddress = signal(false);
  readonly savingAddress = signal(false);
  readonly addingContact = signal(false);
  readonly savingContact = signal(false);
  readonly addingDocument = signal(false);
  readonly savingDocument = signal(false);
  readonly addingExternalLink = signal(false);
  readonly downloadingId = signal<number | null>(null);
  readonly selectedFileName = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  private selectedFile: File | null = null;

  readonly addressForm = this.fb.group({
    country: [''],
    city: [''],
    street: [''],
    postalCode: ['']
  });

  readonly contactForm = this.fb.group({
    name: ['', Validators.required],
    relationship: [''],
    phone: [''],
    email: ['']
  });

  readonly uploadForm = this.fb.group({
    documentType: ['', Validators.required]
  });

  readonly linkForm = this.fb.group({
    documentType: ['', Validators.required],
    fileName: ['', Validators.required],
    fileUrl: [''],
    expiryDate: ['']
  });

  ngOnInit(): void {
    this.employeeId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      employee: this.employeeApi.getById(this.employeeId),
      address: this.addressApi.get(this.employeeId),
      contacts: this.contactApi.list(this.employeeId),
      documents: this.documentApi.list(this.employeeId)
    }).subscribe({
      next: ({ employee, address, contacts, documents }) => {
        this.employee.set(employee);
        this.contacts.set(contacts);
        this.documents.set(documents);
        this.addressForm.patchValue({
          country: address.country ?? '',
          city: address.city ?? '',
          street: address.street ?? '',
          postalCode: address.postalCode ?? ''
        });
        this.loading.set(false);
        this.loadPhoto();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractError(err));
      }
    });
  }

  /**
   * Kept separate from the main forkJoin above - a 404 here (no photo
   * uploaded yet) is the expected default state, not an error, and
   * shouldn't trip the main load's error handler.
   */
  loadPhoto(): void {
    this.photoApi.status(this.employeeId).subscribe((status) => {
      if (!status.hasPhoto) {
        this.photoUrl.set(null);
        return;
      }
      this.photoApi.download(this.employeeId).subscribe((blob) => {
        if (this.photoUrl()) {
          URL.revokeObjectURL(this.photoUrl()!);
        }
        this.photoUrl.set(URL.createObjectURL(blob));
      });
    });
  }

  get initials(): string {
    const emp = this.employee();
    if (!emp) return '';
    return `${emp.firstName.charAt(0)}${emp.lastName.charAt(0)}`.toUpperCase();
  }

  get canEditPhoto(): boolean {
    // Same visibility rule as the backend enforces (self, direct manager,
    // or HR/Admin/Auditor) - a UX convenience only, the API re-checks
    // this regardless.
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'AUDITOR', 'MANAGER', 'EMPLOYEE');
  }

  triggerPhotoPicker(): void {
    this.photoInput.nativeElement.click();
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadingPhoto.set(true);
    this.photoApi.upload(this.employeeId, file).subscribe({
      next: () => {
        this.uploadingPhoto.set(false);
        this.loadPhoto();
      },
      error: (err) => {
        this.uploadingPhoto.set(false);
        this.errorMessage.set(this.extractError(err));
      },
      complete: () => {
        input.value = '';
      }
    });
  }

  removePhoto(): void {
    if (!confirm('Remove this profile photo?')) {
      return;
    }
    this.photoApi.delete(this.employeeId).subscribe(() => {
      if (this.photoUrl()) {
        URL.revokeObjectURL(this.photoUrl()!);
      }
      this.photoUrl.set(null);
    });
  }

  get canEditAddress(): boolean {
    // Mirrors the backend's visibility rule (self, direct manager, or
    // HR/Admin/Auditor) at the UI level only - the API re-checks this
    // regardless, same "route guards are a UX convenience" pattern used
    // throughout this app.
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'AUDITOR', 'MANAGER', 'EMPLOYEE');
  }

  toggleEditAddress(): void {
    this.editingAddress.update((v) => !v);
  }

  saveAddress(): void {
    this.savingAddress.set(true);
    const { country, city, street, postalCode } = this.addressForm.getRawValue();
    this.addressApi
      .upsert(this.employeeId, {
        country: country || undefined,
        city: city || undefined,
        street: street || undefined,
        postalCode: postalCode || undefined
      })
      .subscribe({
        next: () => {
          this.savingAddress.set(false);
          this.editingAddress.set(false);
        },
        error: (err) => {
          this.savingAddress.set(false);
          this.errorMessage.set(this.extractError(err));
        }
      });
  }

  toggleAddContact(): void {
    this.addingContact.update((v) => !v);
  }

  submitContact(): void {
    if (this.contactForm.invalid) {
      return;
    }
    this.savingContact.set(true);
    const { name, relationship, phone, email } = this.contactForm.getRawValue();
    this.contactApi
      .create(this.employeeId, { name: name!, relationship: relationship || undefined, phone: phone || undefined, email: email || undefined })
      .subscribe({
        next: () => {
          this.savingContact.set(false);
          this.addingContact.set(false);
          this.contactForm.reset();
          this.contactApi.list(this.employeeId).subscribe((contacts) => this.contacts.set(contacts));
        },
        error: (err) => {
          this.savingContact.set(false);
          this.errorMessage.set(this.extractError(err));
        }
      });
  }

  deleteContact(contact: EmergencyContact): void {
    if (!confirm(`Remove emergency contact "${contact.name}"?`)) {
      return;
    }
    this.contactApi.delete(this.employeeId, contact.id).subscribe({
      next: () => this.contacts.update((list) => list.filter((c) => c.id !== contact.id)),
      error: (err) => this.errorMessage.set(this.extractError(err))
    });
  }

  toggleAddDocument(): void {
    this.addingDocument.update((v) => !v);
    if (!this.addingDocument()) {
      this.resetUploadState();
    }
  }

  toggleExternalLink(): void {
    this.addingExternalLink.update((v) => !v);
  }

  triggerFilePicker(): void {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile = file;
    this.selectedFileName.set(file?.name ?? null);
  }

  submitUpload(): void {
    if (this.uploadForm.invalid || !this.selectedFile) {
      return;
    }
    const { documentType } = this.uploadForm.getRawValue();
    this.savingDocument.set(true);
    this.documentApi.upload(this.employeeId, documentType!, this.selectedFile).subscribe({
      next: () => {
        this.savingDocument.set(false);
        this.addingDocument.set(false);
        this.uploadForm.reset();
        this.resetUploadState();
        this.documentApi.list(this.employeeId).subscribe((documents) => this.documents.set(documents));
      },
      error: (err) => {
        this.savingDocument.set(false);
        this.errorMessage.set(this.extractError(err));
      }
    });
  }

  submitLink(): void {
    if (this.linkForm.invalid) {
      return;
    }
    this.savingDocument.set(true);
    const { documentType, fileName, fileUrl, expiryDate } = this.linkForm.getRawValue();
    this.documentApi
      .create(this.employeeId, {
        documentType: documentType!,
        fileName: fileName!,
        fileUrl: fileUrl || undefined,
        expiryDate: expiryDate || undefined
      })
      .subscribe({
        next: () => {
          this.savingDocument.set(false);
          this.addingExternalLink.set(false);
          this.linkForm.reset();
          this.documentApi.list(this.employeeId).subscribe((documents) => this.documents.set(documents));
        },
        error: (err) => {
          this.savingDocument.set(false);
          this.errorMessage.set(this.extractError(err));
        }
      });
  }

  downloadDocument(document: EmployeeDocument): void {
    this.downloadingId.set(document.id);
    this.documentApi.download(this.employeeId, document.id).subscribe({
      next: (blob) => {
        this.downloadingId.set(null);
        const url = window.URL.createObjectURL(blob);
        const a = window.document.createElement('a');
        a.href = url;
        a.download = document.fileName;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingId.set(null);
        this.errorMessage.set(this.extractError(err));
      }
    });
  }

  private resetUploadState(): void {
    this.selectedFile = null;
    this.selectedFileName.set(null);
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  deleteDocument(document: EmployeeDocument): void {
    if (!confirm(`Remove document record "${document.fileName}"?`)) {
      return;
    }
    this.documentApi.delete(this.employeeId, document.id).subscribe({
      next: () => this.documents.update((list) => list.filter((d) => d.id !== document.id)),
      error: (err) => this.errorMessage.set(this.extractError(err))
    });
  }

  downloadOfferLetter(): void {
    this.generatingOfferLetter.set(true);
    this.offerLetterApi.downloadPdf(this.employeeId).subscribe({
      next: (blob) => {
        this.generatingOfferLetter.set(false);
        const url = window.URL.createObjectURL(blob);
        const a = window.document.createElement('a');
        a.href = url;
        a.download = `offer-letter-${this.employeeId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.generatingOfferLetter.set(false);
        this.errorMessage.set(this.extractError(err));
      }
    });
  }

  ngOnDestroy(): void {
    if (this.photoUrl()) {
      URL.revokeObjectURL(this.photoUrl()!);
    }
  }

  private extractError(err: unknown): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || 'Something went wrong.';
  }
}
