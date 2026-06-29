package de.hof.dms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.domain.Folder;
import de.hof.dms.mongo.LocalMongoSupport;
import de.hof.dms.repository.FolderRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class FolderApiIntegrationTest {

    private static final String CONTRIBUTOR = "Bearer " + TestJwtDecoderConfig.CONTRIBUTOR_TOKEN;
    private static final String VIEWER = "Bearer " + TestJwtDecoderConfig.VALID_TOKEN;
    private static final String ADMIN = "Bearer " + TestJwtDecoderConfig.ADMIN_TOKEN;
    private static final String MANAGER = "Bearer " + TestJwtDecoderConfig.MANAGER_TOKEN;

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

    private String rootId() {
        return folderRepository.findByPath("/").map(Folder::getId).orElseThrow();
    }

    private JsonNode createFolder(String name, String parentId) throws Exception {
        String body =
                parentId == null
                        ? "{\"name\":\"" + name + "\"}"
                        : "{\"name\":\"" + name + "\",\"parentId\":\"" + parentId + "\"}";
        MvcResult result =
                mockMvc.perform(
                                post("/api/folders")
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void createFolderUnderRootThenListIt() throws Exception {
        String name = "finance-" + UUID.randomUUID();
        JsonNode created = createFolder(name, null);

        assertThat(created.get("path").asText()).isEqualTo("/" + name + "/");
        assertThat(created.get("owner").asText()).isEqualTo("contributor");
        assertThat(created.get("inheritFromParent").asBoolean()).isTrue();
        String createdId = created.get("id").asText();

        // List children of root by explicit parentId.
        MvcResult listResult =
                mockMvc.perform(
                                get("/api/folders")
                                        .param("parentId", rootId())
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode page = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(idsOf(page)).contains(createdId);

        // And without parentId (top-level == children of root) it is also present.
        MvcResult topLevel =
                mockMvc.perform(
                                get("/api/folders").header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode topPage = objectMapper.readTree(topLevel.getResponse().getContentAsString());
        assertThat(idsOf(topPage)).contains(createdId);
    }

    @Test
    void nestedFolderPathIsCorrectTwoLevelsDeep() throws Exception {
        String parentName = "dept-" + UUID.randomUUID();
        JsonNode parent = createFolder(parentName, null);
        String parentId = parent.get("id").asText();
        String parentPath = parent.get("path").asText();

        String childName = "2026";
        JsonNode child = createFolder(childName, parentId);

        assertThat(child.get("path").asText()).isEqualTo(parentPath + childName + "/");
        assertThat(child.get("parentId").asText()).isEqualTo(parentId);

        MvcResult listResult =
                mockMvc.perform(
                                get("/api/folders")
                                        .param("parentId", parentId)
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode page = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(idsOf(page)).contains(child.get("id").asText());
    }

    @Test
    void createUnderMissingParentReturns404() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"orphan\",\"parentId\":\"000000000000000000000001\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/folders")).andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/folders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paginationMetadataIsReturned() throws Exception {
        mockMvc.perform(
                        get("/api/folders")
                                .param("size", "5")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    // ---- role-based management -------------------------------------------

    @Test
    void adminCanCreateAndViewerCannot() throws Exception {
        mockMvc.perform(
                        post("/api/folders")
                                .header(HttpHeaders.AUTHORIZATION, ADMIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"admin-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/folders")
                                .header(HttpHeaders.AUTHORIZATION, VIEWER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"viewer-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanCreateAndManageFoldersInItsDepartment() throws Exception {
        // A contributor in ITDLZ creates a folder → owner_department = ITDLZ.
        String deptFolder = createFolderAs(CONTRIBUTOR, "dept-" + UUID.randomUUID(), null);

        // The department manager may create inside it and rename it.
        String child = createFolderAs(MANAGER, "mgr-child-" + UUID.randomUUID(), deptFolder);
        assertThat(child).isNotBlank();

        String renamed = "renamed-by-mgr-" + UUID.randomUUID();
        mockMvc.perform(
                        patch("/api/folders/" + deptFolder)
                                .header(HttpHeaders.AUTHORIZATION, MANAGER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + renamed + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));
    }

    @Test
    void contributorCanRenameOwnFolderAndItReflectsViaApi() throws Exception {
        String id = createFolderAs(CONTRIBUTOR, "before-" + UUID.randomUUID(), null);
        String newName = "after-" + UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/folders/" + id)
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + newName + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.path").value("/" + newName + "/"));

        // Persisted: the folder view reports the new name.
        mockMvc.perform(get("/api/folders/" + id).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folder.name").value(newName));
    }

    @Test
    void viewerCannotRenameOrDelete() throws Exception {
        String id = createFolderAs(CONTRIBUTOR, "guarded-" + UUID.randomUUID(), null);

        mockMvc.perform(
                        patch("/api/folders/" + id)
                                .header(HttpHeaders.AUTHORIZATION, VIEWER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/folders/" + id).header(HttpHeaders.AUTHORIZATION, VIEWER))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingNonEmptyFolderNeedsRecursive() throws Exception {
        String parent = createFolderAs(CONTRIBUTOR, "parent-" + UUID.randomUUID(), null);
        createFolderAs(CONTRIBUTOR, "child-" + UUID.randomUUID(), parent);

        // Not empty → 409 without recursive.
        mockMvc.perform(delete("/api/folders/" + parent).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isConflict());

        // recursive=true removes the whole subtree.
        mockMvc.perform(
                        delete("/api/folders/" + parent)
                                .param("recursive", "true")
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/folders/" + parent).header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveReparentsFolderAndReflectsUnderNewParent() throws Exception {
        String a = createFolderAs(CONTRIBUTOR, "A-" + UUID.randomUUID(), null);
        String b = createFolderAs(CONTRIBUTOR, "B-" + UUID.randomUUID(), null);
        String childName = "child-" + UUID.randomUUID();
        String child = createFolderAs(CONTRIBUTOR, childName, a);

        // Move child from A to B.
        mockMvc.perform(
                        patch("/api/folders/" + child)
                                .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"parentId\":\"" + b + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(b));

        MvcResult listB =
                mockMvc.perform(
                                get("/api/folders")
                                        .param("parentId", b)
                                        .header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(idsOf(objectMapper.readTree(listB.getResponse().getContentAsString()))).contains(child);
    }

    @Test
    void treeListsReadableFoldersForMovePicker() throws Exception {
        String id = createFolderAs(CONTRIBUTOR, "tree-" + UUID.randomUUID(), null);

        MvcResult result =
                mockMvc.perform(
                                get("/api/folders/tree").header(HttpHeaders.AUTHORIZATION, CONTRIBUTOR))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());

        java.util.List<String> ids = new java.util.ArrayList<>();
        tree.forEach(node -> ids.add(node.get("id").asText()));
        assertThat(ids).contains(id);
    }

    private String createFolderAs(String token, String name, String parentId) throws Exception {
        String body =
                parentId == null
                        ? "{\"name\":\"" + name + "\"}"
                        : "{\"name\":\"" + name + "\",\"parentId\":\"" + parentId + "\"}";
        MvcResult result =
                mockMvc.perform(
                                post("/api/folders")
                                        .header(HttpHeaders.AUTHORIZATION, token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static java.util.List<String> idsOf(JsonNode page) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (JsonNode item : page.get("content")) {
            ids.add(item.get("id").asText());
        }
        return ids;
    }
}
