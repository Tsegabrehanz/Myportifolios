export interface PositionImportRowResult {
  rowNumber: number;
  success: boolean;
  title: string;
  message: string;
}

export interface PositionImportSummaryResponse {
  totalRows: number;
  successCount: number;
  failureCount: number;
  rows: PositionImportRowResult[];
}
