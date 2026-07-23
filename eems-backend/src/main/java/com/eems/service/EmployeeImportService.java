package com.eems.service;

import com.eems.dto.EmployeeDtos.CreateEmployeeRequest;
import com.eems.dto.EmployeeImportDtos.ImportRowResult;
import com.eems.dto.EmployeeImportDtos.ImportSummaryResponse;
import com.eems.repository.DepartmentRepository;
import com.eems.repository.EmployeeRepository;
import com.eems.repository.PositionRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bulk employee import from CSV or XLSX (FR-9.3). Expected columns
 * (header row required, case-insensitive, any order):
 *   firstName, lastName, email, positionTitle, departmentName, hireDate (yyyy-MM-dd),
 *   initialPassword (optional), managerEmail (optional), nationalId (optional)
 *
 * positionTitle must match an existing Position exactly (case-insensitive)
 * - same convention as departmentName. Create positions via POST
 * /api/positions first if a row references one that doesn't exist yet.
 *
 * managerEmail must belong to an employee who already exists - either
 * created in an earlier row of the same file (rows are processed top to
 * bottom, so put managers before their reports) or already in the
 * system. Unlike departmentName/positionTitle, this can't be
 * case-insensitively "created for you" - if the manager doesn't exist
 * yet, the row fails with a clear message.
 *
 * If initialPassword is blank, EmployeeService generates a secure random
 * temporary password (not a fixed placeholder) and flags the account to
 * require a password change on first login. The generated password is
 * shown once, in this response's row message - it isn't stored anywhere
 * and can't be retrieved again, so copy/export the results before
 * navigating away if you need to hand out credentials from a bulk import.
 */
@Service
@RequiredArgsConstructor
public class EmployeeImportService {

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;

    public ImportSummaryResponse importFile(MultipartFile file) {
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
        List<Map<String, String>> rows;
        try {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rows = parseExcel(file);
            } else {
                rows = parseCsv(file);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }

        List<ImportRowResult> results = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2; // +1 for 0-index, +1 for header row
            Map<String, String> row = rows.get(i);
            String email = row.getOrDefault("email", "").trim();

            try {
                CreateEmployeeRequest request = toCreateRequest(row);
                var created = employeeService.create(request);
                String message = "Created";
                if (created.generatedTemporaryPassword() != null) {
                    message += " (temp password: " + created.generatedTemporaryPassword() + ")";
                }
                String actualEmail = created.generatedEmail() != null ? created.generatedEmail() : email;
                results.add(new ImportRowResult(rowNumber, true, actualEmail, message));
                successCount++;
            } catch (Exception e) {
                results.add(new ImportRowResult(rowNumber, false, email, e.getMessage()));
            }
        }

        return new ImportSummaryResponse(rows.size(), successCount, rows.size() - successCount, results);
    }

    private CreateEmployeeRequest toCreateRequest(Map<String, String> row) {
        String firstName = require(row, "firstname");
        String lastName = require(row, "lastname");
        String email = require(row, "email");
        String positionTitle = row.getOrDefault("positiontitle", "").trim();
        String departmentName = row.getOrDefault("departmentname", "").trim();
        String hireDateRaw = require(row, "hiredate");
        String initialPassword = row.getOrDefault("initialpassword", "").trim();
        String managerEmail = row.getOrDefault("manageremail", "").trim();
        String nationalId = row.getOrDefault("nationalid", "").trim();

        Long departmentId = null;
        if (!departmentName.isBlank()) {
            departmentId = departmentRepository.findByNameIgnoreCase(departmentName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + departmentName))
                    .getId();
        }

        Long managerId = null;
        if (!managerEmail.isBlank()) {
            // Manager must already exist - either created in an earlier row of
            // the same file (processed top to bottom, same convention as
            // parentDepartmentName) or already in the system.
            managerId = employeeRepository.findByUserEmail(managerEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown manager email: " + managerEmail))
                    .getId();
        }

        Long positionId = null;
        if (!positionTitle.isBlank()) {
            positionId = positionRepository.findByTitleIgnoreCase(positionTitle)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown position: " + positionTitle))
                    .getId();
        }

        LocalDate hireDate;
        try {
            hireDate = LocalDate.parse(hireDateRaw);
        } catch (Exception e) {
            throw new IllegalArgumentException("hireDate must be yyyy-MM-dd, got: " + hireDateRaw);
        }

        return new CreateEmployeeRequest(
                firstName,
                lastName,
                nationalId.isBlank() ? null : nationalId,
                positionId,
                departmentId,
                managerId,
                hireDate,
                email,
                initialPassword.isBlank() ? null : initialPassword // blank -> EmployeeService generates a secure temp password
        );
    }

    private String require(Map<String, String> row, String key) {
        String value = row.getOrDefault(key, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + key);
        }
        return value;
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
                rows.add(normalizeKeys(record.toMap()));
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
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double value = cell.getNumericCellValue();
                yield (value == Math.floor(value)) ? String.valueOf((long) value) : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Map<String, String> normalizeKeys(Map<String, String> raw) {
        Map<String, String> normalized = new java.util.HashMap<>();
        raw.forEach((k, v) -> normalized.put(k.trim().toLowerCase(), v == null ? "" : v.trim()));
        return normalized;
    }
}
