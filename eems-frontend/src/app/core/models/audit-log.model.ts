export interface AuditLogEntry {
  id: number;
  actorEmail: string;
  entity: string;
  entityId: string;
  action: string;
  detail: string | null;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
