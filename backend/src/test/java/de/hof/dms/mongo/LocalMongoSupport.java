package de.hof.dms.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public final class LocalMongoSupport {

    public static final String LOCAL_URI = "mongodb://localhost:27017/dms";

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
