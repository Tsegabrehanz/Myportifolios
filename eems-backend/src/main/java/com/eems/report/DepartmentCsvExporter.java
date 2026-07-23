package com.eems.report;

import com.eems.dto.PowerBiDtos.DepartmentRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DepartmentCsvExporter {

    public byte[] export(List<DepartmentRow> departments) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("departmentId", "departmentName", "location", "activeHeadcount", "totalHeadcount")
                     .build())) {

            for (DepartmentRow d : departments) {
                printer.printRecord(d.departmentId(), d.departmentName(), d.location(), d.activeHeadcount(), d.totalHeadcount());
            }
            printer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate department CSV export", e);
        }
    }
}
