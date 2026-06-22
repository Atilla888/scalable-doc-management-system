package de.hof.dms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class DocumentApiIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
    private static final String CONTRIBUTOR = "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN;
    private static final String VIEWER = "Bearer " + TestJwtDecoderConfig.VALID_TOKEN;
    private static final String ADMIN = "Bearer " + TestJwtDecoderConfig.ADMIN_TOKEN;

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

    /** Creates a folder owned by the given user so that owner permissions apply. */
    private String createFolderOwnedBy(String owner, String department) {
        Folder folder = new Folder();
        folder.setName("it-folder-" + UUID.randomUUID());
        folder.setPath("/it/" + UUID.randomUUID());
        folder.setParentId(folderRepository.findByPath("/").map(Folder::getId).orElse(null));
        folder.setCreatedAt(Instant.now());

        FolderAcl acl = new FolderAcl();
        acl.setOwner(owner);
        acl.setOwnerDepartment(department);
        acl.setAllowedUserIds(new ArrayList<>());
        acl.setAllowedRoles(new ArrayList<>());
        acl.setAllowedDepartments(new ArrayList<>());
        acl.setAccess(new FolderAccess());
        acl.setInheritFromParent(false);
        folder.setAcl(acl);

        return folderRepository.save(folder).getId();
    }

    private String uploadAsContributor(String parentId) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                multipart("/api/documents")
                                        .file(
                                                new MockMultipartFile(
                                                        "file", "sample.pdf", "application/pdf", PDF_BYTES))
                                        .part(textPart("title", "Integration PDF"))
                                        .part(textPart("documentType", "report"))
                                        .part(textPart("parentId", parentId))
                                        .param("eapCategory", "1001")
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void uploadDownloadAndDeleteDocument() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/api/documents")
                                        .file(
                                                new MockMultipartFile(
                                                        "file", "sample.pdf", "application/pdf", PDF_BYTES))
                                        .part(textPart("title", "Integration PDF"))
                                        .part(textPart("documentType", "report"))
                                        .part(textPart("parentId", parentId))
                                        .param("eapCategory", "1001")
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("UPLOADED"))
                        .andExpect(jsonPath("$.ocrStatus").value("pending"))
                        .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String documentId = uploadJson.get("id").asText();

        MvcResult metadataResult =
                mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
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
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andExpect(
                                header -> assertThat(header.getResponse().getContentType())
                                        .contains("application/pdf"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF_BYTES);

        mockMvc.perform(delete("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadToMissingFolderReturns404() throws Exception {
        mockMvc.perform(
                        multipart("/api/documents")
                                .file(new MockMultipartFile("file", "sample.pdf", "application/pdf", PDF_BYTES))
                                .part(textPart("title", "Orphan doc"))
                                .part(textPart("documentType", "report"))
                                .part(textPart("parentId", "000000000000000000000001"))
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTitleReturns400() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");

        mockMvc.perform(
                        multipart("/api/documents")
                                .file(new MockMultipartFile("file", "sample.pdf", "application/pdf", PDF_BYTES))
                                .part(textPart("title", " "))
                                .part(textPart("documentType", "report"))
                                .part(textPart("parentId", parentId))
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title is required"));
    }

    @Test
    void viewerWithoutAccessGetsForbiddenNotNotFound() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");
        String documentId = uploadAsContributor(parentId);

        // The document exists, but the viewer has no read access → 403 (not 404).
        mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/documents/" + documentId + "/download")
                                .header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAnyDocument() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");
        String documentId = uploadAsContributor(parentId);

        mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedPermissionUpdateReturns403() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");
        String documentId = uploadAsContributor(parentId);

        String aclJson =
                "{\"owner\":\"contributor\",\"ownerDepartment\":\"ITDLZ\",\"allowedUserIds\":[],"
                        + "\"allowedRoles\":[\"dms_viewer\"],\"allowedDepartments\":[],"
                        + "\"access\":{\"read\":true,\"create\":false,\"update\":false,\"delete\":false,\"managePermissions\":false},"
                        + "\"inheritFromParent\":false}";

        // Viewer lacks managePermissions → 403.
        mockMvc.perform(
                        put("/api/documents/" + documentId + "/permissions")
                                .header(HttpHeaders.AUTHORIZATION, VIEWER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(aclJson))
                .andExpect(status().isForbidden());

        // Owner (contributor) may manage permissions → 200, and viewer can then read.
        mockMvc.perform(
                        put("/api/documents/" + documentId + "/permissions")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(aclJson))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/" + documentId).header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isOk());
    }

    @Test
    void metadataUpdateRequiresUpdatePermission() throws Exception {
        String parentId = createFolderOwnedBy("contributor", "ITDLZ");
        String documentId = uploadAsContributor(parentId);

        String body = "{\"title\":\"Renamed\"}";

        // Viewer cannot update → 403.
        mockMvc.perform(
                        put("/api/documents/" + documentId + "/metadata")
                                .header(HttpHeaders.AUTHORIZATION, VIEWER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());

        // Owner can update → 200.
        mockMvc.perform(
                        put("/api/documents/" + documentId + "/metadata")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"));
    }

    private static MockPart textPart(String name, String value) {
        return new MockPart(name, null, value.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
    }
}
