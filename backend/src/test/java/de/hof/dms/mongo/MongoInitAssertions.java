package de.hof.dms.mongo;

import de.hof.dms.domain.Department;
import de.hof.dms.domain.Folder;
import de.hof.dms.repository.FolderRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

final class MongoInitAssertions {

  private MongoInitAssertions() {}

  static void assertCollectionsExist(MongoTemplate mongoTemplate) {
    assertThat(mongoTemplate.getCollectionNames())
        .contains("folders", "documents", "eap_sequences", "audit_logs", "departments");
  }

  static void assertDefaultDepartmentSeeded(MongoTemplate mongoTemplate) {
    Department itdlz =
        mongoTemplate.findOne(
            new Query(Criteria.where("code").is("ITDLZ")), Department.class);

    assertThat(itdlz).isNotNull();
    assertThat(itdlz.isActive()).isTrue();
    assertThat(itdlz.getDisplayName()).isNotBlank();

    long count =
        mongoTemplate.count(new Query(Criteria.where("code").is("ITDLZ")), Department.class);
    assertThat(count).isEqualTo(1);
  }

  static void assertDepartmentIndexes(MongoTemplate mongoTemplate) {
    boolean uniqueCodeIndex =
        StreamSupport.stream(
                mongoTemplate.getDb().getCollection("departments").listIndexes().spliterator(),
                false)
            .anyMatch(
                doc ->
                    "code_1".equals(doc.getString("name"))
                        && Boolean.TRUE.equals(doc.getBoolean("unique")));
    assertThat(uniqueCodeIndex).as("unique index on departments.code").isTrue();
  }

  static void assertRootFolder(FolderRepository folderRepository) {
    Folder root = folderRepository.findByPath("/").orElseThrow();

    assertThat(root.getName()).isEqualTo("/");
    assertThat(root.getParentId()).isNull();
    assertThat(root.getAcl()).isNotNull();
    assertThat(root.getAcl().getOwner()).isEqualTo("dms_admin");
    assertThat(root.getAcl().getAllowedRoles()).contains("dms_admin");
  }

  static void assertSingleRootFolder(MongoTemplate mongoTemplate) {
    long count = mongoTemplate.count(new Query(Criteria.where("path").is("/")), Folder.class);
    assertThat(count).isEqualTo(1);
  }

  static void assertFolderIndexes(MongoTemplate mongoTemplate) {
    assertIndexNames(mongoTemplate, "folders", "parent_id_1", "path_1", "name_1");
  }

  static void assertDocumentIndexes(MongoTemplate mongoTemplate) {
    Set<String> names = indexNames(mongoTemplate, "documents");

    assertThat(names)
        .contains(
            "folder_id_1",
            "acl.allowed_roles_1",
            "acl.allowed_departments_1",
            "acl.owner_1",
            "eap_number_1",
            "uploader_id_1",
            "ocr_status_1_upload_date_1");
    assertThat(names.stream().anyMatch(name -> name.contains("text"))).isTrue();
  }

  static void assertGridFsReachable(MongoTemplate mongoTemplate, GridFsTemplate gridFsTemplate) {
    gridFsTemplate.store(
        new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)), "gridfs-probe.txt");

    assertThat(
            gridFsTemplate.findOne(
                Query.query(Criteria.where("filename").is("gridfs-probe.txt"))))
        .isNotNull();

    assertThat(mongoTemplate.getCollectionNames()).contains("fs.files", "fs.chunks");
  }

  private static void assertIndexNames(
      MongoTemplate mongoTemplate, String collection, String... expected) {
    assertThat(indexNames(mongoTemplate, collection)).contains(expected);
  }

  private static Set<String> indexNames(MongoTemplate mongoTemplate, String collection) {
    return StreamSupport.stream(
            mongoTemplate.getDb().getCollection(collection).listIndexes().spliterator(), false)
        .map(doc -> doc.getString("name"))
        .collect(Collectors.toSet());
  }
}
