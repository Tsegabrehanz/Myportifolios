package com.eems.report;

import com.eems.dto.EmployeeDtos.EmployeeResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deliberately mirrors the same columns EmployeeResponse already
 * exposes over the API - no nationalId or other sensitive fields here,
 * matching what the app already treats as safe to show broadly.
 */
@Component
public class EmployeeCsvExporter {

    public byte[] export(List<EmployeeResponse> employees) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("id", "employeeCode", "firstName", "lastName", "email", "positionTitle", "departmentName", "managerName", "hireDate", "exitDate", "status")
                     .build())) {

            for (EmployeeResponse e : employees) {
                printer.printRecord(
                        e.id(), e.employeeCode(), e.firstName(), e.lastName(), e.email(), e.positionTitle(),
                        e.departmentName(), e.managerName(), e.hireDate(), e.exitDate(), e.status()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate employee CSV export", ex);
        }
    }
}
