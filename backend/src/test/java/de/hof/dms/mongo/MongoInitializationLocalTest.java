package de.hof.dms.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.hof.dms.repository.FolderRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test against MongoDB from {@code docker compose up} on localhost:27017.
 * Use this when Testcontainers cannot reach Docker Desktop from the JVM (common on Windows).
 */
@DataMongoTest
class MongoInitializationLocalTest {

  private static final String LOCAL_URI = "mongodb://localhost:27017/dms";

  @DynamicPropertySource
  static void registerLocalMongo(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", () -> LOCAL_URI);
  }

  @BeforeAll
  static void requireComposeMongo() {
    assumeTrue(isLocalMongoAvailable(), () -> "Start Compose MongoDB: cd infra/docker-compose && docker compose up -d mongodb");
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

  private static boolean isLocalMongoAvailable() {
    try (MongoClient client = MongoClients.create(LOCAL_URI)) {
      client.getDatabase("dms").runCommand(new org.bson.Document("ping", 1));
      return client.getDatabase("dms").getCollection("folders").countDocuments() >= 1;
    } catch (Exception ex) {
      return false;
    }
  }
}
