package io.github.azhardev77.tenderengine.service.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfBoxPageExtractor {

    /** Pages with fewer non-whitespace characters than this are likely scanned images. */
    private static final int SCANNED_PAGE_CHAR_THRESHOLD = 50;

    /** A line with 2+ runs of consecutive whitespace (spaces/tabs) looks tabular. */
    private static final double DENSE_TABLE_LINE_RATIO = 0.30;

    public record ExtractedPage(int pageNumber, String text, boolean scanned, boolean hasDenseTables) {}

    public List<ExtractedPage> extract(Path pdfPath) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(pdfPath.toString()))) {
            int totalPages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int p = 1; p <= totalPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);

                boolean scanned = countNonWhitespace(text) < SCANNED_PAGE_CHAR_THRESHOLD;
                boolean denseTables = detectDenseTables(text);
                pages.add(new ExtractedPage(p, text, scanned, denseTables));
            }
        }
        return pages;
    }

    private static int countNonWhitespace(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) count++;
        }
        return count;
    }

    /**
     * A page "has dense tables" when a significant fraction of its non-blank lines
     * contain runs of 2+ consecutive whitespace characters (space/tab), which is
     * a simple signal for column-aligned tabular data.
     */
    private static boolean detectDenseTables(String text) {
        List<String> nonBlankLines = text.lines()
                .filter(line -> !line.isBlank())
                .toList();
        if (nonBlankLines.isEmpty()) return false;

        long tabularLines = nonBlankLines.stream()
                .filter(line -> line.matches(".*[ \\t]{2,}.*"))
                .count();
        return (double) tabularLines / nonBlankLines.size() >= DENSE_TABLE_LINE_RATIO;
    }
}
