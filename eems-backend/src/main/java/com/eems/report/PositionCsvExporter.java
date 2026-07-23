package com.eems.report;

import com.eems.dto.PositionDtos.PositionResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class PositionCsvExporter {

    public byte[] export(List<PositionResponse> positions) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("id", "title", "grade", "salaryBand", "jobDescription", "departmentName")
                     .build())) {

            for (PositionResponse p : positions) {
                printer.printRecord(p.id(), p.title(), p.grade(), p.salaryBand(), p.jobDescription(), p.departmentName());
            }
            printer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate position CSV export", e);
        }
    }
}
