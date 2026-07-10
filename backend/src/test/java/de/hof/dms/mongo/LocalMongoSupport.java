package de.hof.dms.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public final class LocalMongoSupport {

    /**
     * MongoDB the integration tests run against. Defaults to an unauthenticated
     * local instance on 27017 — exactly what the CI pipeline provides. Override
     * with the DMS_TEST_MONGODB_URI environment variable; for the Compose
     * stack, which publishes MongoDB on 127.0.0.1:27018 with authentication:
     * {@code mongodb://dms_app:<MONGO_APP_PASSWORD>@localhost:27018/dms?authSource=dms}
     */
    public static final String LOCAL_URI =
            System.getenv().getOrDefault("DMS_TEST_MONGODB_URI", "mongodb://localhost:27017/dms");

    private LocalMongoSupport() {}

    public static boolean isAvailable() {
        try (MongoClient client = MongoClients.create(LOCAL_URI)) {
            client.getDatabase("dms").runCommand(new org.bson.Document("ping", 1));
            return client.getDatabase("dms").getCollection("folders").countDocuments() >= 1;
        } catch (Exception ex) {
            return false;
        }
    }
}
