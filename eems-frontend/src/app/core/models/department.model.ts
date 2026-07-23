export interface Department {
  id: number;
  name: string;
  parentDepartmentId: number | null;
  location: string | null;
}

export interface CreateDepartmentRequest {
  name: string;
  parentDepartmentId?: number;
  location?: string;
}
