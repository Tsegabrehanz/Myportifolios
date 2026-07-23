import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateEmployeeDocumentRequest, EmployeeDocument } from '../models/employee-document.model';

@Injectable({ providedIn: 'root' })
export class EmployeeDocumentApiService {
  constructor(private http: HttpClient) {}

  list(employeeId: number): Observable<EmployeeDocument[]> {
    return this.http.get<EmployeeDocument[]>(`${environment.apiUrl}/employees/${employeeId}/documents`);
  }

  /** Metadata-only record (no file bytes) - fileUrl points wherever the file actually lives externally. */
  create(employeeId: number, request: CreateEmployeeDocumentRequest): Observable<EmployeeDocument> {
    return this.http.post<EmployeeDocument>(`${environment.apiUrl}/employees/${employeeId}/documents`, request);
  }

  /** Real upload - the file's actual bytes are sent and stored server-side. */
  upload(employeeId: number, documentType: string, file: File): Observable<EmployeeDocument> {
    const formData = new FormData();
    formData.append('documentType', documentType);
    formData.append('file', file);
    return this.http.post<EmployeeDocument>(`${environment.apiUrl}/employees/${employeeId}/documents/upload`, formData);
  }

  /** Only works for documents where downloadable === true (real uploads, not metadata-only records). */
  download(employeeId: number, documentId: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/employees/${employeeId}/documents/${documentId}/download`, { responseType: 'blob' });
  }

  delete(employeeId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/employees/${employeeId}/documents/${documentId}`);
  }
}
