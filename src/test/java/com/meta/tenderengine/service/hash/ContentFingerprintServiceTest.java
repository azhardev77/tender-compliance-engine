package com.meta.tenderengine.service.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.meta.tenderengine.service.hash.ContentFingerprintService.FileFingerprint;

class ContentFingerprintServiceTest {

    private final ContentFingerprintService service = new ContentFingerprintService();

    @Test
    void sameBytesSameSha256() throws Exception {
        byte[] content = "Hello, tender world!".getBytes();
        MockMultipartFile file1 = new MockMultipartFile("file", "a.pdf", "application/pdf", content);
        MockMultipartFile file2 = new MockMultipartFile("file", "b.pdf", "application/pdf", content);

        FileFingerprint fp1 = service.fingerprint(file1);
        FileFingerprint fp2 = service.fingerprint(file2);
        try {
            assertThat(fp1.sha256()).isEqualTo(fp2.sha256());
            assertThat(fp1.sizeBytes()).isEqualTo(fp2.sizeBytes());
        } finally {
            java.nio.file.Files.deleteIfExists(fp1.tempPath());
            java.nio.file.Files.deleteIfExists(fp2.tempPath());
        }
    }

    @Test
    void differentBytesDifferentSha256() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "a.pdf", "application/pdf", "AAA".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "b.pdf", "application/pdf", "BBB".getBytes());

        FileFingerprint fp1 = service.fingerprint(file1);
        FileFingerprint fp2 = service.fingerprint(file2);
        try {
            assertThat(fp1.sha256()).isNotEqualTo(fp2.sha256());
        } finally {
            java.nio.file.Files.deleteIfExists(fp1.tempPath());
            java.nio.file.Files.deleteIfExists(fp2.tempPath());
        }
    }

    @Test
    void normalizedHashIgnoresWhitespaceAndRepeatedHeaders() {
        // "Page X of N" appears on every page — it should be dropped as a header/footer
        String page1 = "Page 1 of 3\nSome real content about tender compliance\nAnother line";
        String page2 = "Page 2 of 3\nDifferent content on second page";
        String page3 = "Page 3 of 3\nYet more content here";

        String hash1 = service.normalizedTextHash(List.of(page1, page2, page3));

        // Same content with extra whitespace everywhere
        String page1ws = "  Page 1 of 3\n  Some   real   content   about   tender   compliance  \n  Another  line  ";
        String page2ws = "  Page 2 of 3\n  Different   content   on   second   page  ";
        String page3ws = "  Page 3 of 3\n  Yet   more   content   here  ";

        String hash2 = service.normalizedTextHash(List.of(page1ws, page2ws, page3ws));

        assertThat(hash1).isEqualTo(hash2);

        // Different content should give a different hash
        String hash3 = service.normalizedTextHash(List.of("totally different", "no similarity"));
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    void tempFileIsWritten() throws Exception {
        byte[] content = new byte[1024];
        java.util.Arrays.fill(content, (byte) 'x');
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);

        FileFingerprint fp = service.fingerprint(file);
        try {
            assertThat(fp.tempPath()).exists();
            assertThat(fp.sizeBytes()).isEqualTo(1024);
            assertThat(fp.sha256()).hasSize(64); // 32 bytes hex-encoded
        } finally {
            java.nio.file.Files.deleteIfExists(fp.tempPath());
        }
    }
}
