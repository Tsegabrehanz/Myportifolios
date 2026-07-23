import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PasswordResetResponse, UserSummary } from '../models/user-admin.model';
import { UserRole } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AdminUserApiService {
  private readonly baseUrl = `${environment.apiUrl}/admin/users`;

  constructor(private http: HttpClient) {}

  list(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(this.baseUrl);
  }

  updateRole(id: number, role: UserRole): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.baseUrl}/${id}/role`, { role });
  }

  updateEnabled(id: number, enabled: boolean): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.baseUrl}/${id}/enabled`, { enabled });
  }

  resetPassword(id: number): Observable<PasswordResetResponse> {
    return this.http.post<PasswordResetResponse>(`${this.baseUrl}/${id}/reset-password`, {});
  }
}
