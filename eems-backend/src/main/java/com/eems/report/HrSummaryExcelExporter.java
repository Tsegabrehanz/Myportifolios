package com.eems.report;

import com.eems.dto.ReportDtos.DepartmentHeadcount;
import com.eems.dto.ReportDtos.HrSummaryReport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class HrSummaryExcelExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public byte[] export(HrSummaryReport report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);

            writeOverviewSheet(workbook, headerStyle, report);
            writeDepartmentSheet(workbook, headerStyle, report);
            writeLeaveSheet(workbook, headerStyle, report);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel report", e);
        }
    }

    private void writeOverviewSheet(Workbook workbook, CellStyle headerStyle, HrSummaryReport report) {
        Sheet sheet = workbook.createSheet("Overview");
        int rowIdx = 0;

        Row title = sheet.createRow(rowIdx++);
        title.createCell(0).setCellValue("EEMS - HR Analytics Summary");
        Row generated = sheet.createRow(rowIdx++);
        generated.createCell(0).setCellValue("Generated at: " + TIMESTAMP_FORMAT.format(report.generatedAt()));
        rowIdx++;

        rowIdx = writeKeyValueRow(sheet, headerStyle, rowIdx, "Metric", "Value");
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Total active employees", String.valueOf(report.totalActiveEmployees()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Total headcount (all statuses)", String.valueOf(report.totalHeadcountAllStatuses()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Average tenure (years)", String.valueOf(report.averageTenureYears()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "New hires (last 12 months)", String.valueOf(report.newHiresLast12Months()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Offboarded (last 12 months)", String.valueOf(report.offboardedLast12Months()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Attrition rate (%)", String.valueOf(report.attritionRatePercent()));
        rowIdx = writeKeyValueRow(sheet, null, rowIdx, "Pending leave approvals", String.valueOf(report.pendingLeaveApprovals()));
        rowIdx++;

        rowIdx = writeKeyValueRow(sheet, headerStyle, rowIdx, "Status", "Headcount");
        for (Map.Entry<String, Long> entry : report.headcountByStatus().entrySet()) {
            rowIdx = writeKeyValueRow(sheet, null, rowIdx, entry.getKey(), String.valueOf(entry.getValue()));
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 20 * 256);
    }

    private void writeDepartmentSheet(Workbook workbook, CellStyle headerStyle, HrSummaryReport report) {
        Sheet sheet = workbook.createSheet("Headcount by Department");
        int rowIdx = writeKeyValueRow(sheet, headerStyle, 0, "Department", "Headcount");
        for (DepartmentHeadcount d : report.headcountByDepartment()) {
            rowIdx = writeKeyValueRow(sheet, null, rowIdx, d.departmentName(), String.valueOf(d.headcount()));
        }
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 15 * 256);
    }

    private void writeLeaveSheet(Workbook workbook, CellStyle headerStyle, HrSummaryReport report) {
        Sheet sheet = workbook.createSheet("Leave");
        int rowIdx = writeKeyValueRow(sheet, headerStyle, 0, "Leave status", "Count");
        for (Map.Entry<String, Long> entry : report.leaveRequestsByStatus().entrySet()) {
            rowIdx = writeKeyValueRow(sheet, null, rowIdx, entry.getKey(), String.valueOf(entry.getValue()));
        }
        rowIdx++;
        rowIdx = writeKeyValueRow(sheet, headerStyle, rowIdx, "Leave type", "Count");
        for (Map.Entry<String, Long> entry : report.leaveRequestsByType().entrySet()) {
            rowIdx = writeKeyValueRow(sheet, null, rowIdx, entry.getKey(), String.valueOf(entry.getValue()));
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 15 * 256);
    }

    private int writeKeyValueRow(Sheet sheet, CellStyle style, int rowIdx, String col1, String col2) {
        Row row = sheet.createRow(rowIdx);
        Cell c1 = row.createCell(0);
        c1.setCellValue(col1);
        Cell c2 = row.createCell(1);
        c2.setCellValue(col2);
        if (style != null) {
            c1.setCellStyle(style);
            c2.setCellStyle(style);
        }
        return rowIdx + 1;
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }
}
