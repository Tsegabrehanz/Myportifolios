package com.eems.dto;

import java.util.List;

public class EmployeeImportDtos {

    public record ImportRowResult(
            int rowNumber,
            boolean success,
            String email,
            String message
    ) {}

    public record ImportSummaryResponse(
            int totalRows,
            int successCount,
            int failureCount,
            List<ImportRowResult> rows
    ) {}
}
