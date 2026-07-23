export interface EmergencyContact {
  id: number;
  employeeId: number;
  name: string;
  relationship: string | null;
  phone: string | null;
  email: string | null;
}

export interface CreateEmergencyContactRequest {
  name: string;
  relationship?: string;
  phone?: string;
  email?: string;
}
