export interface EmployeeIdFormat {
  prefix: string;
  suffix: string;
  nextSequence: number;
  exampleNextCode: string;
}

export interface UpdateEmployeeIdFormatRequest {
  prefix: string;
  suffix: string;
}
