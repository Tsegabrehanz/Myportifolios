package com.eems.dto;

public class EmployeeAddressDtos {

    public record AddressResponse(
            Long employeeId,
            String country,
            String city,
            String street,
            String postalCode
    ) {}

    public record UpsertAddressRequest(
            String country,
            String city,
            String street,
            String postalCode
    ) {}
}
