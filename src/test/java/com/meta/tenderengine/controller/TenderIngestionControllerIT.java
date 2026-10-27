package com.meta.tenderengine.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TenderIngestionControllerIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void uploadTwice_secondIsDuplicate() throws Exception {
        byte[] pdf = generatePdf("Unique content for dedup test");

        MvcResult first = mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "tender.pdf", "application/pdf", pdf)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn();

        String firstId = extractField(first, "id");

        MvcResult second = mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "tender.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andReturn();

        String secondId = extractField(second, "id");
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void uploadCorrigendum_withValidParent() throws Exception {
        byte[] parentPdf = generatePdf("Parent tender content");
        MvcResult parentResult = mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "parent.pdf", "application/pdf", parentPdf)))
                .andExpect(status().isAccepted())
                .andReturn();

        String parentId = extractField(parentResult, "id");

        byte[] corrigendumPdf = generatePdf("Corrigendum content differs from parent");
        mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "corrigendum.pdf", "application/pdf", corrigendumPdf))
                        .param("docType", "CORRIGENDUM")
                        .param("parentTenderId", parentId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void uploadCorrigendum_missingParent_returns400() throws Exception {
        byte[] pdf = generatePdf("Orphan corrigendum");
        mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "corrigendum.pdf", "application/pdf", pdf))
                        .param("docType", "CORRIGENDUM")
                        .param("parentTenderId", "00000000-0000-0000-0000-000000000099"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parent tender not found: 00000000-0000-0000-0000-000000000099"));
    }

    @Test
    void uploadNonPdf_returns400() throws Exception {
        byte[] notPdf = "This is not a PDF file at all".getBytes();
        mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "readme.txt", "text/plain", notPdf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not a PDF file (invalid magic bytes)"));
    }

    @Test
    void getStatus_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/tenders/00000000-0000-0000-0000-000000000001/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatus_afterUpload_returnsDocument() throws Exception {
        byte[] pdf = generatePdf("Status check content");
        MvcResult result = mvc.perform(multipart("/api/tenders")
                        .file(new MockMultipartFile("file", "status-test.pdf", "application/pdf", pdf)))
                .andExpect(status().isAccepted())
                .andReturn();

        String id = extractField(result, "id");

        mvc.perform(get("/api/tenders/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fileName").value("status-test.pdf"))
                .andExpect(jsonPath("$.docType").value("MAIN_TENDER"));
    }

    private String extractField(MvcResult result, String field) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get(field).asText();
    }

    private static byte[] generatePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
