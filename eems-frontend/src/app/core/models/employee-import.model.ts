export interface ImportRowResult {
  rowNumber: number;
  success: boolean;
  email: string;
  message: string;
}

export interface ImportSummaryResponse {
  totalRows: number;
  successCount: number;
  failureCount: number;
  rows: ImportRowResult[];
}
