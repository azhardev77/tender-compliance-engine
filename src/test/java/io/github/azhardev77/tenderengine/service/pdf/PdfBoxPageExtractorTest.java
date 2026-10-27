package io.github.azhardev77.tenderengine.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.azhardev77.tenderengine.service.pdf.PdfBoxPageExtractor.ExtractedPage;

class PdfBoxPageExtractorTest {

    private final PdfBoxPageExtractor extractor = new PdfBoxPageExtractor();

    @Test
    void extractsTextFromMultiPagePdf(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("test.pdf");
        String[] pageTexts = {
                "This is the first page of the tender document with enough content to be clearly recognized as text not scanned",
                "Second page contains eligibility criteria and requirements for the bidder along with detailed specifications",
                "Third page has the financial terms and conditions including payment schedule and penalty clauses for delay"
        };
        createPdf(pdf, pageTexts);

        List<ExtractedPage> pages = extractor.extract(pdf);

        assertThat(pages).hasSize(3);
        for (int i = 0; i < pageTexts.length; i++) {
            assertThat(pages.get(i).pageNumber()).isEqualTo(i + 1);
            assertThat(pages.get(i).text()).contains(pageTexts[i]);
            assertThat(pages.get(i).scanned()).isFalse();
        }
    }

    @Test
    void detectsScannedPage(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("scanned.pdf");
        createPdf(pdf, new String[]{
                "This page has plenty of real text content that clearly exceeds the scanned detection threshold used by the extractor",
                ""
        });

        List<ExtractedPage> pages = extractor.extract(pdf);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).scanned()).isFalse();
        assertThat(pages.get(1).scanned()).isTrue();
    }

    @Test
    void detectsDenseTables(@TempDir Path tempDir) throws Exception {
        Path pdf = tempDir.resolve("table.pdf");
        String tableContent = """
                Item No    Description        Quantity    Unit Price
                1          Steel Rods          500         45000
                2          Cement Bags          200         350
                3          Sand (cubic m)       100         1200
                4          Bricks               5000        8
                """;
        createPdf(pdf, new String[]{tableContent});

        List<ExtractedPage> pages = extractor.extract(pdf);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).hasDenseTables()).isTrue();
    }

    private static void createPdf(Path path, String[] pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.newLineAtOffset(50, 700);
                        cs.setLeading(14.5f);
                        for (String line : text.split("\n")) {
                            cs.showText(line);
                            cs.newLine();
                        }
                        cs.endText();
                    }
                }
            }
            doc.save(path.toFile());
        }
    }
}
