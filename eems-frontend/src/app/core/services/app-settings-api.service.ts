import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogoStatus } from '../models/app-settings.model';

/** Logo GET endpoints are public/unauthenticated server-side - no token needed even before login. */
@Injectable({ providedIn: 'root' })
export class AppSettingsApiService {
  private readonly baseUrl = `${environment.apiUrl}/app-settings/logo`;

  constructor(private http: HttpClient) {}

  status(): Observable<LogoStatus> {
    return this.http.get<LogoStatus>(`${this.baseUrl}/status`);
  }

  download(): Observable<Blob> {
    return this.http.get(this.baseUrl, { responseType: 'blob' });
  }

  upload(file: File): Observable<LogoStatus> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<LogoStatus>(this.baseUrl, formData);
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
