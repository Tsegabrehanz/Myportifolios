import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OfferLetterApiService {
  constructor(private http: HttpClient) {}

  /** Same access as salary - self or HR_ADMIN/SUPER_ADMIN only, enforced server-side. */
  downloadPdf(employeeId: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/employees/${employeeId}/offer-letter/export.pdf`, { responseType: 'blob' });
  }
}
