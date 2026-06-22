package de.hof.dms.cmis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.mongo.LocalMongoSupport;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for the minimal CMIS interface (Browser/JSON binding).
 * Runs against the Compose MongoDB (relies on the seeded root folder; ensures
 * the documents text index for the CONTAINS query test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class CmisApiIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 cmis".getBytes(StandardCharsets.UTF_8);
    private static final String CONTRIBUTOR = "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN;
    private static final String VIEWER = "Bearer " + TestJwtDecoderConfig.VALID_TOKEN;
    private static final String ROOT = "/cmis/browser/dms/root";

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

    @Autowired private MockMvc mockMvc;
    @Autowired private FolderRepository folderRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ObjectMapper objectMapper;

    private String rootId() {
        return folderRepository.findByPath("/").map(Folder::getId).orElseThrow();
    }

    // ---- acceptance -------------------------------------------------------

    @Test
    void getRepositoryInfoReturnsIdAndName() throws Exception {
        mockMvc.perform(get("/cmis/browser").header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dms.repositoryId").value("dms"))
                .andExpect(jsonPath("$.dms.repositoryName").isNotEmpty());

        mockMvc.perform(
                        get("/cmis/browser/dms")
                                .param("cmisselector", "repositoryInfo")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value("dms"));
    }

    @Test
    void getChildrenReturnsOnlyObjectsTheUserCanRead() throws Exception {
        // A folder the contributor owns (created through CMIS) is visible to it.
        String folderId = propertyOf(createFolderAsContributor(), "cmis:objectId");

        // A document owned by someone else, no inheritance → invisible to the viewer.
        String restrictedDocId = insertRestrictedDocument("someone-else", rootId(), "indexed");

        JsonNode contributorChildren = childrenOf(rootId(), CONTRIBUTOR);
        assertThat(objectIds(contributorChildren)).contains(folderId);

        JsonNode viewerChildren = childrenOf(rootId(), VIEWER);
        assertThat(objectIds(viewerChildren)).doesNotContain(restrictedDocId);
    }

    @Test
    void getObjectOnUnauthorizedDocumentReturnsPermissionDeniedFault() throws Exception {
        String docId = insertRestrictedDocument("contributor", rootId(), "indexed");

        mockMvc.perform(
                        get(ROOT)
                                .param("cmisselector", "object")
                                .param("objectId", docId)
                                .header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exception").value("permissionDenied"));

        // The owner can read the same object.
        mockMvc.perform(
                        get(ROOT)
                                .param("cmisselector", "object")
                                .param("objectId", docId)
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succinctProperties.['cmis:objectId']").value(docId));
    }

    @Test
    void createDocumentIsVisibleViaRestApiWithSameMetadata() throws Exception {
        String name = "CMIS Created " + UUID.randomUUID();

        MvcResult created =
                mockMvc.perform(
                                multipart(ROOT)
                                        .file(new MockMultipartFile("content", "c.pdf", "application/pdf", PDF_BYTES))
                                        .param("cmisaction", "createDocument")
                                        .param("propertyId[0]", "cmis:name")
                                        .param("propertyValue[0]", name)
                                        .param("propertyId[1]", "dms:documentType")
                                        .param("propertyValue[1]", "report")
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.succinctProperties.['cmis:baseTypeId']").value("cmis:document"))
                        .andReturn();
        String docId =
                propertyOf(objectMapper.readTree(created.getResponse().getContentAsString()), "cmis:objectId");

        // Same document, same metadata, visible through the REST API.
        mockMvc.perform(get("/api/documents/" + docId).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(name))
                .andExpect(jsonPath("$.documentType").value("report"));
    }

    @Test
    void queryWithContainsReturnsOnlyAuthorizedDocuments() throws Exception {
        ensureTextIndex();
        String keyword = "kw" + UUID.randomUUID().toString().replace("-", "");
        String docId = insertRestrictedDocument("contributor", rootId(), "indexed", "Report " + keyword);

        // Owner sees the match...
        JsonNode owned = query("CONTAINS('" + keyword + "')", CONTRIBUTOR);
        assertThat(objectIds(owned)).contains(docId);

        // ...the viewer, who has no access, sees nothing.
        JsonNode unauthorized = query("CONTAINS('" + keyword + "')", VIEWER);
        assertThat(objectIds(unauthorized)).doesNotContain(docId);
    }

    @Test
    void viewerCannotCreateViaCmis() throws Exception {
        // createDocument as dms_viewer → permission denied.
        mockMvc.perform(
                        multipart(ROOT)
                                .file(new MockMultipartFile("content", "c.pdf", "application/pdf", PDF_BYTES))
                                .param("cmisaction", "createDocument")
                                .param("propertyId[0]", "cmis:name")
                                .param("propertyValue[0]", "Nope " + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exception").value("permissionDenied"));

        // createFolder as dms_viewer → permission denied.
        mockMvc.perform(
                        post(ROOT)
                                .param("cmisaction", "createFolder")
                                .param("propertyId[0]", "cmis:name")
                                .param("propertyValue[0]", "Nope " + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exception").value("permissionDenied"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/cmis/browser")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----------------------------------------------------------

    private JsonNode createFolderAsContributor() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(ROOT)
                                        .param("cmisaction", "createFolder")
                                        .param("propertyId[0]", "cmis:name")
                                        .param("propertyValue[0]", "cmis-folder-" + UUID.randomUUID())
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode childrenOf(String folderId, String token) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(ROOT)
                                        .param("cmisselector", "children")
                                        .param("objectId", folderId)
                                        .header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode query(String statement, String token) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/cmis/browser/dms")
                                        .param("cmisselector", "query")
                                        .param("statement", "SELECT * FROM cmis:document WHERE " + statement)
                                        .header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String propertyOf(JsonNode object, String propertyId) {
        return object.get("succinctProperties").get(propertyId).asText();
    }

    /** Collects cmis:objectId from a children list ({objects:[{object:{...}}]}) or query ({results:[{...}]}). */
    private List<String> objectIds(JsonNode listing) {
        List<String> ids = new ArrayList<>();
        if (listing.has("objects")) {
            listing.get("objects").forEach(e -> ids.add(propertyOf(e.get("object"), "cmis:objectId")));
        }
        if (listing.has("results")) {
            listing.get("results").forEach(o -> ids.add(propertyOf(o, "cmis:objectId")));
        }
        return ids;
    }

    private String insertRestrictedDocument(String owner, String folderId, String indexingStatus) {
        return insertRestrictedDocument(owner, folderId, indexingStatus, "Restricted " + UUID.randomUUID());
    }

    /** A document readable only by its owner (no role/department/inheritance grants). */
    private String insertRestrictedDocument(
            String owner, String folderId, String indexingStatus, String title) {
        DocumentRecord doc = new DocumentRecord();
        doc.setTitle(title);
        doc.setDocumentType("report");
        doc.setEapNumber("EAP-CMIS-" + UUID.randomUUID());
        doc.setFolderId(folderId);
        doc.setUploadDate(Instant.now());
        doc.setUploaderId(owner);
        doc.setOcrStatus("not_required");
        doc.setIndexingStatus(indexingStatus);
        doc.setDocumentStatus("active");
        doc.setContentType("application/pdf");
        doc.setFileName("restricted.pdf");
        doc.setFileSize(PDF_BYTES.length);

        FolderAcl acl = new FolderAcl();
        acl.setOwner(owner);
        acl.setAllowedUserIds(new ArrayList<>(List.of(owner)));
        acl.setAllowedRoles(new ArrayList<>());
        acl.setAllowedDepartments(new ArrayList<>());
        FolderAccess access = new FolderAccess();
        access.setRead(true);
        acl.setAccess(access);
        acl.setInheritFromParent(false);
        doc.setAcl(acl);

        return documentRepository.save(doc).getId();
    }

    private void ensureTextIndex() {
        TextIndexDefinition index =
                new TextIndexDefinition.TextIndexDefinitionBuilder()
                        .onField("title")
                        .onField("description")
                        .onField("ocr_text")
                        .build();
        mongoTemplate.indexOps("documents").ensureIndex(index);
    }
}
