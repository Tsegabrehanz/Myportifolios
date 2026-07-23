export interface DepartmentImportRowResult {
  rowNumber: number;
  success: boolean;
  name: string;
  message: string;
}

export interface DepartmentImportSummaryResponse {
  totalRows: number;
  successCount: number;
  failureCount: number;
  rows: DepartmentImportRowResult[];
}
