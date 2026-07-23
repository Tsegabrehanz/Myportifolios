import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLogEntry, PageResponse } from '../models/audit-log.model';

@Injectable({ providedIn: 'root' })
export class AuditLogApiService {
  private readonly baseUrl = `${environment.apiUrl}/audit-logs`;

  constructor(private http: HttpClient) {}

  list(page: number, size: number, entity?: string, actor?: string): Observable<PageResponse<AuditLogEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (entity) params = params.set('entity', entity);
    if (actor) params = params.set('actor', actor);
    return this.http.get<PageResponse<AuditLogEntry>>(this.baseUrl, { params });
  }
}
