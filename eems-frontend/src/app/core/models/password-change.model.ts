export interface UpdatePhoneNumberRequest {
  phoneNumber: string;
}

export interface InitiateChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface InitiateChangeResponse {
  message: string;
  maskedPhoneNumber: string;
}

export interface ConfirmChangeRequest {
  otpCode: string;
}
