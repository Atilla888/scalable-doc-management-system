db = db.getSiblingDB("dms");

if (!db.getCollectionNames().includes("folders")) {
    db.createCollection("folders");
}

if (!db.getCollectionNames().includes("documents")) {
    db.createCollection("documents");
}

if (!db.getCollectionNames().includes("eap_sequences")) {
    db.createCollection("eap_sequences");
}

if (!db.getCollectionNames().includes("audit_logs")) {
    db.createCollection("audit_logs");
}

db.folders.createIndex({ parent_id: 1 });
db.folders.createIndex({ path: 1 });
db.folders.createIndex({ name: 1 });

db.documents.createIndex({ folder_id: 1 });
db.documents.createIndex({
    title: "text",
    description: "text",
    ocr_text: "text"
});
db.documents.createIndex({
    "acl.allowed_roles": 1
});
db.documents.createIndex({
    "acl.allowed_departments": 1
});
db.documents.createIndex({
    "acl.owner": 1
});
db.documents.createIndex(
    { eap_number: 1 },
    { unique: true }
);
db.documents.createIndex({
    uploader_id: 1
});
db.documents.createIndex({
    ocr_status: 1,
    upload_date: 1
});

db.folders.updateOne(
    { path: "/" },

    {
        $setOnInsert: {
            name: "/",
            path: "/",
            parent_id: null,

            acl: {
                owner: "dms_admin",
                owner_department: null,

                allowed_user_ids: [],

                // All authenticated roles may READ the root listing; write actions
                // are decided by the RBAC resolver (admin, department manager,
                // contributor-create, or explicit ACLs on child items).
                allowed_roles: [
                    "dms_admin",
                    "dms_department_manager",
                    "dms_contributor",
                    "dms_viewer"
                ],

                allowed_departments: [],

                access: {
                    read: true,
                    create: false,
                    update: false,
                    delete: false,
                    managePermissions: false
                },

                inheritFromParent: false
            },

            created_at: new Date()
        }
    },

    { upsert: true }
);

// GridFS (fs.files / fs.chunks) is not pre-created; MongoDB creates those collections on first GridFS write.
print("DMS database initialized: collections, indexes, and root folder (path=/).");
