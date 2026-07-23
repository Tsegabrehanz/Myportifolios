package com.eems.dto;

public class EmployeePhotoDtos {

    public record PhotoStatusResponse(
            Long employeeId,
            boolean hasPhoto
    ) {}
}
