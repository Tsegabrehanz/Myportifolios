package com.eems.service;

import com.eems.dto.PositionDtos.CreatePositionRequest;
import com.eems.dto.PositionImportDtos.ImportRowResult;
import com.eems.dto.PositionImportDtos.ImportSummaryResponse;
import com.eems.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bulk position import from CSV or XLSX (same dual-format support as
 * EmployeeImportService). Expected columns (header row required,
 * case-insensitive, any order): title, grade, salaryBand, departmentName
 * (optional). departmentName must match an existing department exactly
 * (case-insensitive) - create departments first if a row references one
 * that doesn't exist yet.
 */
@Service
@RequiredArgsConstructor
public class PositionImportService {

    private final PositionService positionService;
    private final DepartmentRepository departmentRepository;

    public ImportSummaryResponse importFile(MultipartFile file) {
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        List<Map<String, String>> rows;
        try {
            rows = (filename.endsWith(".xlsx") || filename.endsWith(".xls")) ? parseExcel(file) : parseCsv(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }

        List<ImportRowResult> results = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            Map<String, String> row = rows.get(i);
            String title = row.getOrDefault("title", "").trim();

            try {
                CreatePositionRequest request = toCreateRequest(row);
                positionService.create(request);
                results.add(new ImportRowResult(rowNumber, true, title, "Created"));
                successCount++;
            } catch (Exception e) {
                results.add(new ImportRowResult(rowNumber, false, title, e.getMessage()));
            }
        }

        return new ImportSummaryResponse(rows.size(), successCount, rows.size() - successCount, results);
    }

    private CreatePositionRequest toCreateRequest(Map<String, String> row) {
        String title = row.getOrDefault("title", "").trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("Missing required column: title");
        }
        String grade = row.getOrDefault("grade", "").trim();
        String salaryBand = row.getOrDefault("salaryband", "").trim();
        String jobDescription = row.getOrDefault("jobdescription", "").trim();
        String departmentName = row.getOrDefault("departmentname", "").trim();

        Long departmentId = null;
        if (!departmentName.isBlank()) {
            departmentId = departmentRepository.findByNameIgnoreCase(departmentName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + departmentName))
                    .getId();
        }

        return new CreatePositionRequest(
                title,
                grade.isBlank() ? null : grade,
                salaryBand.isBlank() ? null : salaryBand,
                jobDescription.isBlank() ? null : jobDescription,
                departmentId
        );
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build();
            CSVParser parser = format.parse(reader);
            for (CSVRecord record : parser) {
                Map<String, String> normalized = new java.util.HashMap<>();
                record.toMap().forEach((k, v) -> normalized.put(k.trim().toLowerCase(), v == null ? "" : v.trim()));
                rows.add(normalized);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim().toLowerCase());
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) {
                    continue;
                }
                Map<String, String> rowMap = new java.util.HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = dataRow.getCell(c);
                    rowMap.put(headers.get(c), cellToString(cell));
                }
                rows.add(rowMap);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield (value == Math.floor(value)) ? String.valueOf((long) value) : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
