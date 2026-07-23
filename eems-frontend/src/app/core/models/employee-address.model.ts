export interface EmployeeAddress {
  employeeId: number;
  country: string | null;
  city: string | null;
  street: string | null;
  postalCode: string | null;
}

export interface UpsertAddressRequest {
  country?: string;
  city?: string;
  street?: string;
  postalCode?: string;
}
