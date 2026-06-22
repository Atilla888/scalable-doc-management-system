package de.hof.dms.mongo;

import de.hof.dms.repository.FolderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/**
 * Spins up MongoDB via Testcontainers and runs {@code scripts/mongo-init.js}.
 * Skipped when the JVM cannot talk to Docker ({@code disabledWithoutDocker}).
 * On Windows Docker Desktop, prefer {@link MongoInitializationLocalTest} with Compose.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MongoInitializationTest {

  private static final Path INIT_SCRIPT =
      Path.of(System.getProperty("user.dir")).getParent().resolve("scripts/mongo-init.js");

  @Container
  static MongoDBContainer mongoDBContainer =
      new MongoDBContainer("mongo:7")
          .withCopyFileToContainer(
              MountableFile.forHostPath(INIT_SCRIPT),
              "/docker-entrypoint-initdb.d/01-mongo-init.js");

  @DynamicPropertySource
  static void registerMongoProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", mongoDBContainer::getConnectionString);
  }

  @Autowired private FolderRepository folderRepository;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private GridFsTemplate gridFsTemplate;

  @Test
  void collectionsExist() {
    MongoInitAssertions.assertCollectionsExist(mongoTemplate);
  }

  @Test
  void rootFolderExistsWithDmsAdminOwnership() {
    MongoInitAssertions.assertRootFolder(folderRepository);
  }

  @Test
  void seedDoesNotDuplicateRootFolder() {
    MongoInitAssertions.assertSingleRootFolder(mongoTemplate);
  }

  @Test
  void folderIndexesExist() {
    MongoInitAssertions.assertFolderIndexes(mongoTemplate);
  }

  @Test
  void documentIndexesExist() {
    MongoInitAssertions.assertDocumentIndexes(mongoTemplate);
  }

  @Test
  void gridFsIsReachable() {
    MongoInitAssertions.assertGridFsReachable(mongoTemplate, gridFsTemplate);
  }
}
