export interface Position {
  id: number;
  title: string;
  grade: string | null;
  salaryBand: string | null;
  jobDescription: string | null;
  departmentId: number | null;
  departmentName: string | null;
}

export interface CreatePositionRequest {
  title: string;
  grade?: string;
  salaryBand?: string;
  jobDescription?: string;
  departmentId?: number;
}

export interface UpdatePositionRequest {
  grade?: string;
  salaryBand?: string;
  jobDescription?: string;
  departmentId?: number;
}
