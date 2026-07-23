package com.eems.dto;

import java.util.List;

public class PositionImportDtos {

    public record ImportRowResult(
            int rowNumber,
            boolean success,
            String title,
            String message
    ) {}

    public record ImportSummaryResponse(
            int totalRows,
            int successCount,
            int failureCount,
            List<ImportRowResult> rows
    ) {}
}
