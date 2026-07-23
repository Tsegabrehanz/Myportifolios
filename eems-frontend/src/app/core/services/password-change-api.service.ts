import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ConfirmChangeRequest,
  InitiateChangeRequest,
  InitiateChangeResponse,
  UpdatePhoneNumberRequest
} from '../models/password-change.model';

@Injectable({ providedIn: 'root' })
export class PasswordChangeApiService {
  constructor(private http: HttpClient) {}

  updatePhoneNumber(request: UpdatePhoneNumberRequest): Observable<void> {
    return this.http.patch<void>(`${environment.apiUrl}/users/me/phone`, request);
  }

  initiate(request: InitiateChangeRequest): Observable<InitiateChangeResponse> {
    return this.http.post<InitiateChangeResponse>(`${environment.apiUrl}/auth/change-password/initiate`, request);
  }

  confirm(request: ConfirmChangeRequest): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/change-password/confirm`, request);
  }
}
