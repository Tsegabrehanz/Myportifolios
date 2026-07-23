import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PhotoStatus } from '../models/employee-photo.model';

@Injectable({ providedIn: 'root' })
export class EmployeePhotoApiService {
  constructor(private http: HttpClient) {}

  status(employeeId: number): Observable<PhotoStatus> {
    return this.http.get<PhotoStatus>(`${environment.apiUrl}/employees/${employeeId}/photo/status`);
  }

  /** Returns a Blob - the caller is responsible for turning it into an object URL (and revoking it when done). */
  download(employeeId: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/employees/${employeeId}/photo`, { responseType: 'blob' });
  }

  upload(employeeId: number, file: File): Observable<PhotoStatus> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PhotoStatus>(`${environment.apiUrl}/employees/${employeeId}/photo`, formData);
  }

  delete(employeeId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/employees/${employeeId}/photo`);
  }
}
