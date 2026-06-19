package de.hof.dms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.mongo.LocalMongoSupport;
import de.hof.dms.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/search}: RBAC filtering at query time,
 * exclusion of deleted / not-yet-indexed documents, pagination, payload safety
 * (no ACL / no leak of restricted documents), and 401 for bad tokens.
 *
 * <p>Runs against the Compose MongoDB (it relies on the {@code documents} text
 * index created by {@code scripts/mongo-init.js}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class SearchApiIntegrationTest {

    private static final String CONTRIBUTOR = "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN;
    private static final String VIEWER = "Bearer " + TestJwtDecoderConfig.VALID_TOKEN;
    private static final String ADMIN = "Bearer " + TestJwtDecoderConfig.ADMIN_TOKEN;
    private static final String EXPIRED = "Bearer " + TestJwtDecoderConfig.EXPIRED_TOKEN;

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
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ObjectMapper objectMapper;

    // ---- acceptance: authorized vs unauthorized ---------------------------

    @Test
    void ownerFindsDocumentByTitleKeyword() throws Exception {
        String keyword = uniqueKeyword();
        String id = insertDoc(titleWith(keyword), "contributor", List.of(), List.of(), "indexed", "active");

        JsonNode results = search(CONTRIBUTOR, keyword, "");

        assertThat(idsOf(results)).contains(id);
    }

    @Test
    void documentGrantedByRoleIsFoundByThatRole() throws Exception {
        String keyword = uniqueKeyword();
        // Owned by someone else, but shared with dms_viewer via the ACL role list.
        String id = insertDoc(
                titleWith(keyword), "someone-else", List.of("dms_viewer"), List.of(), "indexed", "active");

        JsonNode results = search(VIEWER, keyword, "");

        assertThat(idsOf(results)).contains(id);
    }

    @Test
    void unauthorizedUserGetsZeroResultsAndNoTraceOfDocument() throws Exception {
        String keyword = uniqueKeyword();
        String restrictedTitle = titleWith(keyword);
        String id = insertDoc(restrictedTitle, "contributor", List.of(), List.of(), "indexed", "active");

        MvcResult result =
                mockMvc.perform(get("/api/search?query=" + keyword).header(HttpHeaders.AUTHORIZATION, VIEWER))
                        .andExpect(status().isOk())
                        .andReturn();
        String body = result.getResponse().getContentAsString();

        // Zero results, and no fragment of the restricted document anywhere in the payload.
        assertThat(idsOf(objectMapper.readTree(body))).isEmpty();
        assertThat(body).doesNotContain(keyword);
        assertThat(body).doesNotContain(id);
        assertThat(body).doesNotContain(restrictedTitle);
    }

    @Test
    void adminFindsAllDocumentsMatchingKeyword() throws Exception {
        String keyword = uniqueKeyword();
        String a = insertDoc(titleWith(keyword), "contributor", List.of(), List.of(), "indexed", "active");
        String b = insertDoc(titleWith(keyword), "someone-else", List.of(), List.of(), "indexed", "active");

        JsonNode results = search(ADMIN, keyword, "");

        assertThat(idsOf(results)).contains(a, b);
    }

    // ---- acceptance: exclusions -------------------------------------------

    @Test
    void deletedDocumentsDoNotAppear() throws Exception {
        String keyword = uniqueKeyword();
        insertDoc(titleWith(keyword), "contributor", List.of(), List.of(), "indexed", "deleted");

        // Admin would see any active match; the only match here is deleted.
        JsonNode results = search(ADMIN, keyword, "");

        assertThat(idsOf(results)).isEmpty();
    }

    @Test
    void notYetIndexedDocumentsDoNotAppear() throws Exception {
        String keyword = uniqueKeyword();
        insertDoc(titleWith(keyword), "contributor", List.of(), List.of(), "pending", "active");

        JsonNode results = search(ADMIN, keyword, "");

        assertThat(idsOf(results)).isEmpty();
    }

    // ---- acceptance: payload safety ---------------------------------------

    @Test
    void payloadContainsNoAclFields() throws Exception {
        String keyword = uniqueKeyword();
        insertDoc(titleWith(keyword), "contributor", List.of(), List.of(), "indexed", "active");

        MvcResult result =
                mockMvc.perform(get("/api/search?query=" + keyword).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(body).doesNotContain("acl");
        assertThat(body).doesNotContain("allowed_roles");
        assertThat(body).doesNotContain("allowedRoles");
        assertThat(body).doesNotContain("allowedDepartments");
        assertThat(body).doesNotContain("ocrText");
        assertThat(body).doesNotContain("ocr_text");
    }

    // ---- acceptance: pagination -------------------------------------------

    @Test
    void paginationRespectsPageAndLimit() throws Exception {
        String keyword = uniqueKeyword();
        Set<String> all = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            all.add(insertDoc(
                    titleWith(keyword), "contributor", List.of(), List.of(), "indexed", "active",
                    Instant.now().minusSeconds(i)));
        }

        List<String> firstPage = idsOf(search(CONTRIBUTOR, keyword, "&page=0&limit=2"));
        List<String> secondPage = idsOf(search(CONTRIBUTOR, keyword, "&page=1&limit=2"));

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);

        Set<String> paged = new HashSet<>(firstPage);
        paged.addAll(secondPage);
        // Pages are disjoint and together cover exactly the three inserted documents.
        assertThat(paged).isEqualTo(all);
    }

    // ---- acceptance: auth -------------------------------------------------

    @Test
    void expiredTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/search?query=anything").header(HttpHeaders.AUTHORIZATION, EXPIRED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/search?query=anything")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----------------------------------------------------------

    private JsonNode search(String token, String query, String extraParams) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/api/search?query=" + query + extraParams)
                                        .header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> idsOf(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String titleWith(String keyword) {
        return "Quarterly report " + keyword;
    }

    private String insertDoc(
            String title,
            String owner,
            List<String> roles,
            List<String> departments,
            String indexingStatus,
            String documentStatus) {
        return insertDoc(title, owner, roles, departments, indexingStatus, documentStatus, Instant.now());
    }

    private String insertDoc(
            String title,
            String owner,
            List<String> roles,
            List<String> departments,
            String indexingStatus,
            String documentStatus,
            Instant uploadDate) {

        DocumentRecord doc = new DocumentRecord();
        doc.setTitle(title);
        doc.setDocumentType("report");
        doc.setEapNumber("EAP-TEST-" + UUID.randomUUID());
        doc.setFolderId("test-folder-id");
        doc.setUploadDate(uploadDate);
        doc.setUploaderId(owner);
        doc.setOcrStatus("completed");
        doc.setIndexingStatus(indexingStatus);
        doc.setDocumentStatus(documentStatus);

        FolderAcl acl = new FolderAcl();
        acl.setOwner(owner);
        acl.setAllowedUserIds(new ArrayList<>());
        acl.setAllowedRoles(new ArrayList<>(roles));
        acl.setAllowedDepartments(new ArrayList<>(departments));
        acl.setAccess(new FolderAccess());
        acl.setInheritFromParent(false);
        doc.setAcl(acl);

        return documentRepository.save(doc).getId();
    }
}
