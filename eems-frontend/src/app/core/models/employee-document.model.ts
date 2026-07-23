export interface EmployeeDocument {
  id: number;
  employeeId: number;
  documentType: string;
  fileName: string;
  fileUrl: string | null;
  contentType: string | null;
  fileSizeBytes: number | null;
  downloadable: boolean; // true when this was a real upload (has stored bytes) - false for metadata-only records with just a fileUrl
  expiryDate: string | null;
  uploadedAt: string;
}

/**
 * Metadata-only record (no bytes uploaded) - fileUrl points wherever the
 * file actually lives externally. For a real upload, use
 * EmployeeDocumentApiService.upload() instead (multipart).
 */
export interface CreateEmployeeDocumentRequest {
  documentType: string;
  fileName: string;
  fileUrl?: string;
  expiryDate?: string;
}
