package com.eems.dto;

import java.util.List;

public class DepartmentImportDtos {

    public record ImportRowResult(
            int rowNumber,
            boolean success,
            String name,
            String message
    ) {}

    public record ImportSummaryResponse(
            int totalRows,
            int successCount,
            int failureCount,
            List<ImportRowResult> rows
    ) {}
}
