package com.eems.report;

import com.eems.dto.ReportDtos.DepartmentHeadcount;
import com.eems.dto.ReportDtos.HrSummaryReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class HrSummaryPdfExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 16;
    private static final PDType1Font TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font HEADING_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] export(HrSummaryReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PdfCursor cursor = new PdfCursor(document, page);

            cursor.writeLine("EEMS - HR Analytics Summary", TITLE_FONT, 16);
            cursor.writeLine("Generated at: " + TIMESTAMP_FORMAT.format(report.generatedAt()), BODY_FONT, 10);
            cursor.blankLine();

            cursor.writeLine("Overview", HEADING_FONT, 13);
            cursor.writeKeyValue("Total active employees", String.valueOf(report.totalActiveEmployees()));
            cursor.writeKeyValue("Total headcount (all statuses)", String.valueOf(report.totalHeadcountAllStatuses()));
            cursor.writeKeyValue("Average tenure (years)", String.valueOf(report.averageTenureYears()));
            cursor.writeKeyValue("New hires (last 12 months)", String.valueOf(report.newHiresLast12Months()));
            cursor.writeKeyValue("Offboarded (last 12 months)", String.valueOf(report.offboardedLast12Months()));
            cursor.writeKeyValue("Attrition rate", report.attritionRatePercent() + "%");
            cursor.writeKeyValue("Pending leave approvals", String.valueOf(report.pendingLeaveApprovals()));
            cursor.blankLine();

            cursor.writeLine("Headcount by Status", HEADING_FONT, 13);
            for (Map.Entry<String, Long> entry : report.headcountByStatus().entrySet()) {
                cursor.writeKeyValue(entry.getKey(), String.valueOf(entry.getValue()));
            }
            cursor.blankLine();

            cursor.writeLine("Headcount by Department", HEADING_FONT, 13);
            for (DepartmentHeadcount d : report.headcountByDepartment()) {
                cursor.writeKeyValue(d.departmentName(), String.valueOf(d.headcount()));
            }
            cursor.blankLine();

            cursor.writeLine("Leave Requests by Status", HEADING_FONT, 13);
            for (Map.Entry<String, Long> entry : report.leaveRequestsByStatus().entrySet()) {
                cursor.writeKeyValue(entry.getKey(), String.valueOf(entry.getValue()));
            }
            cursor.blankLine();

            cursor.writeLine("Leave Requests by Type", HEADING_FONT, 13);
            for (Map.Entry<String, Long> entry : report.leaveRequestsByType().entrySet()) {
                cursor.writeKeyValue(entry.getKey(), String.valueOf(entry.getValue()));
            }

            cursor.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }

    /**
     * Small helper that tracks the current Y position and opens a new
     * page automatically when content runs off the bottom margin -
     * PDFBox has no built-in flowing-text layout, so this is the
     * minimum needed to avoid writing off the page.
     */
    private static class PdfCursor {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        PdfCursor(PDDocument document, PDPage page) throws IOException {
            this.document = document;
            this.page = page;
            this.stream = new PDPageContentStream(document, page);
            this.y = page.getMediaBox().getHeight() - MARGIN;
        }

        void writeLine(String text, PDType1Font font, float fontSize) throws IOException {
            ensureSpace();
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        void writeKeyValue(String key, String value) throws IOException {
            ensureSpace();
            stream.beginText();
            stream.setFont(BODY_FONT, 10);
            stream.newLineAtOffset(MARGIN + 10, y);
            stream.showText(key + ": " + value);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        void blankLine() {
            y -= LINE_HEIGHT / 2;
        }

        private void ensureSpace() throws IOException {
            if (y < MARGIN) {
                stream.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = page.getMediaBox().getHeight() - MARGIN;
            }
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
