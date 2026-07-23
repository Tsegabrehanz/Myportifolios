export type JobPostingVisibility = 'INTERNAL' | 'EXTERNAL' | 'BOTH';
export type JobPostingStatus = 'DRAFT' | 'OPEN' | 'CLOSED';

export interface JobPosting {
  id: number;
  title: string;
  description: string | null;
  departmentId: number | null;
  departmentName: string | null;
  positionId: number | null;
  positionTitle: string | null;
  visibility: JobPostingVisibility;
  status: JobPostingStatus;
  location: string | null;
  postedDate: string;
  closingDate: string | null;
}

export interface CreateJobPostingRequest {
  title: string;
  description?: string;
  departmentId?: number;
  positionId?: number;
  visibility: JobPostingVisibility;
  location?: string;
  postedDate: string;
  closingDate?: string;
}

export interface UpdateJobPostingRequest {
  title?: string;
  description?: string;
  departmentId?: number;
  positionId?: number;
  visibility?: JobPostingVisibility;
  status?: JobPostingStatus;
  location?: string;
  closingDate?: string;
}
