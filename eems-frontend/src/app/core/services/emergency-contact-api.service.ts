import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateEmergencyContactRequest, EmergencyContact } from '../models/emergency-contact.model';

@Injectable({ providedIn: 'root' })
export class EmergencyContactApiService {
  constructor(private http: HttpClient) {}

  list(employeeId: number): Observable<EmergencyContact[]> {
    return this.http.get<EmergencyContact[]>(`${environment.apiUrl}/employees/${employeeId}/emergency-contacts`);
  }

  create(employeeId: number, request: CreateEmergencyContactRequest): Observable<EmergencyContact> {
    return this.http.post<EmergencyContact>(`${environment.apiUrl}/employees/${employeeId}/emergency-contacts`, request);
  }

  delete(employeeId: number, contactId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/employees/${employeeId}/emergency-contacts/${contactId}`);
  }
}
