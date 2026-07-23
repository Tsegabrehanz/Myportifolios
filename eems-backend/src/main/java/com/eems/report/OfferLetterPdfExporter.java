package com.eems.report;

import com.eems.dto.OfferLetterDtos.OfferLetterData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A genuine, generated PDF offer letter - not a template file, actual
 * PDFBox drawing with word-wrapped paragraphs. Content is generic
 * boilerplate (this app has no e-signature, benefits catalog, or legal
 * review workflow) - treat the output as a starting draft to review and
 * customize, not something to send to a candidate unedited.
 */
@Component
public class OfferLetterPdfExporter {

    private static final float MARGIN = 60;
    private static final float LINE_HEIGHT = 16;
    private static final float FONT_SIZE = 11;
    private static final PDFont BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    public byte[] export(OfferLetterData data) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PdfCursor cursor = new PdfCursor(document, page);

            cursor.writeLine("EEMS", BOLD_FONT, 16);
            cursor.blankLine();
            cursor.writeLine(DATE_FORMAT.format(data.issueDate()), BODY_FONT, FONT_SIZE);
            cursor.blankLine();
            cursor.writeLine(data.employeeName(), BODY_FONT, FONT_SIZE);
            cursor.blankLine();

            cursor.writeParagraph("Dear " + data.employeeName() + ",", BODY_FONT);
            cursor.blankLine();

            String openingLine = "We are pleased to confirm your position as " + data.positionTitle()
                    + (data.departmentName() != null ? " within the " + data.departmentName() + " department" : "")
                    + ", effective " + DATE_FORMAT.format(data.startDate()) + ".";
            cursor.writeParagraph(openingLine, BODY_FONT);
            cursor.blankLine();

            if (data.compensationLine() != null) {
                cursor.writeParagraph(data.compensationLine(), BODY_FONT);
                cursor.blankLine();
            }

            cursor.writeParagraph(
                    "This offer is subject to the standard terms of employment described in your "
                            + "employment contract and company policies in effect from time to time. "
                            + "Your employment is at-will and either party may terminate the relationship "
                            + "in accordance with applicable law and company policy.",
                    BODY_FONT);
            cursor.blankLine();

            cursor.writeParagraph(
                    "Please sign and return a copy of this letter to indicate your acceptance of "
                            + "this offer. We look forward to you joining the team.",
                    BODY_FONT);
            cursor.blankLine();
            cursor.blankLine();

            cursor.writeLine("Sincerely,", BODY_FONT, FONT_SIZE);
            cursor.blankLine();
            cursor.writeLine("Human Resources", BODY_FONT, FONT_SIZE);
            cursor.writeLine("EEMS", BODY_FONT, FONT_SIZE);
            cursor.blankLine();
            cursor.blankLine();
            cursor.writeLine("Accepted by: _______________________________     Date: _______________", BODY_FONT, FONT_SIZE);

            cursor.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate offer letter PDF", e);
        }
    }

    /** Same page-overflow-safe cursor pattern as HrSummaryPdfExporter, plus word-wrapped paragraph support for offer-letter prose. */
    private static class PdfCursor {
        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private final float usableWidth;

        PdfCursor(PDDocument document, PDPage page) throws IOException {
            this.document = document;
            this.page = page;
            this.stream = new PDPageContentStream(document, page);
            this.y = page.getMediaBox().getHeight() - MARGIN;
            this.usableWidth = page.getMediaBox().getWidth() - (2 * MARGIN);
        }

        void writeLine(String text, PDFont font, float fontSize) throws IOException {
            ensureSpace();
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        void writeParagraph(String text, PDFont font) throws IOException {
            for (String line : wrap(text, font, FONT_SIZE, usableWidth)) {
                writeLine(line, font, FONT_SIZE);
            }
        }

        void blankLine() {
            y -= LINE_HEIGHT / 2;
        }

        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                float width = font.getStringWidth(candidate) / 1000 * fontSize;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
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
