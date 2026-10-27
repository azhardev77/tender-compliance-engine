package io.github.azhardev77.tenderengine.service.hash;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContentFingerprintService {

    public record FileFingerprint(Path tempPath, String sha256, long sizeBytes) {}

    /**
     * Streams the upload to a temp file in one pass while computing SHA-256.
     * The caller is responsible for deleting the temp file.
     */
    public FileFingerprint fingerprint(MultipartFile file) throws IOException {
        MessageDigest digest = sha256Digest();
        Path tempFile = Files.createTempFile("tender-upload-", ".pdf");

        try (InputStream in = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(tempFile)) {
            dis.transferTo(out);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }

        String hash = HexFormat.of().formatHex(digest.digest());
        long size = Files.size(tempFile);
        return new FileFingerprint(tempFile, hash, size);
    }

    /**
     * Computes a normalized-text SHA-256 from extracted page texts.
     * Normalization: lowercase, collapse whitespace, drop lines that appear
     * on more than half the pages (running headers/footers).
     */
    public String normalizedTextHash(List<String> pageTexts) {
        Map<String, Long> lineFrequency = pageTexts.stream()
                .flatMap(text -> text.lines())
                .map(ContentFingerprintService::normalizeLine)
                .filter(line -> !line.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        int halfPages = Math.max(1, pageTexts.size() / 2);

        MessageDigest digest = sha256Digest();
        for (String pageText : pageTexts) {
            pageText.lines()
                    .map(ContentFingerprintService::normalizeLine)
                    .filter(line -> !line.isBlank())
                    .filter(line -> lineFrequency.getOrDefault(line, 0L) <= halfPages)
                    .forEach(line -> digest.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static String normalizeLine(String line) {
        return line.toLowerCase().strip().replaceAll("\\s+", " ");
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available in every JRE", e);
        }
    }
}
