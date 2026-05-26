package de.hof.dms.controller;

import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.mongo.LocalMongoSupport;
import de.hof.dms.repository.FolderRepository;
import de.hof.dms.service.EapNumberService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class DocumentApiIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);

    @DynamicPropertySource
    static void registerLocalMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> LocalMongoSupport.LOCAL_URI);
    }

    @BeforeAll
    static void requireComposeMongo() {
        assumeTrue(
                LocalMongoSupport.isAvailable(),
                () -> "Start Compose MongoDB: cd infra/docker-compose && docker compose up -d mongodb");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadDownloadAndDeleteDocument() throws Exception {
        String parentId = folderRepository.findByPath("/").orElseThrow().getId();
        String auth = "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN;

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/documents")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        "sample.pdf",
                                                        "application/pdf",
                                                        PDF_BYTES))
                                        .part(textPart("title", "Integration PDF"))
                                        .part(textPart("documentType", "report"))
                                        .part(textPart("parentId", parentId))
                                        .param("eapCategory", "1001")
                                        .header(HttpHeaders.AUTHORIZATION, auth))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("UPLOADED"))
                        .andExpect(jsonPath("$.ocrStatus").value("pending"))
                        .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String documentId = uploadJson.get("id").asText();

        MvcResult metadataResult =
                mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, auth))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Integration PDF"))
                        .andExpect(jsonPath("$.documentType").value("report"))
                        .andExpect(jsonPath("$.parentId").value(parentId))
                        .andExpect(jsonPath("$.documentStatus").value("active"))
                        .andReturn();
        String eap =
                objectMapper
                        .readTree(metadataResult.getResponse().getContentAsString())
                        .get("eapNumber")
                        .asText();
        assertThat(eap).matches(EapNumberService.EAP_FORMAT);

        byte[] downloaded =
                mockMvc.perform(
                                get("/api/documents/" + documentId + "/download")
                                        .header(HttpHeaders.AUTHORIZATION, auth))
                        .andExpect(status().isOk())
                        .andExpect(
                                header -> assertThat(header.getResponse().getContentType())
                                        .contains("application/pdf"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF_BYTES);

        mockMvc.perform(delete("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadToMissingFolderReturns404() throws Exception {
        mockMvc.perform(
                        multipart("/api/documents")
                                .file(
                                        new MockMultipartFile(
                                                "file", "sample.pdf", "application/pdf", PDF_BYTES))
                                .part(textPart("title", "Orphan doc"))
                                .part(textPart("documentType", "report"))
                                .part(textPart("parentId", "000000000000000000000001"))
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTitleReturns400() throws Exception {
        String parentId = folderRepository.findByPath("/").orElseThrow().getId();

        mockMvc.perform(
                        multipart("/api/documents")
                                .file(
                                        new MockMultipartFile(
                                                "file", "sample.pdf", "application/pdf", PDF_BYTES))
                                .part(textPart("title", " "))
                                .part(textPart("documentType", "report"))
                                .part(textPart("parentId", parentId))
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title is required"));
    }

    private static MockPart textPart(String name, String value) {
        return new MockPart(name, null, value.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
    }
}
