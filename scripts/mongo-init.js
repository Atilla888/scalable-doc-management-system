db = db.getSiblingDB("dms");

// Application user with readWrite on dms only, the backend and OCR worker use this
// instead of the root account. Credentials come from injected script globals when
// present (the replica-set initiator passes them that way), otherwise from the
// container environment (the single-node docker-entrypoint path). Creating the user
// is idempotent so this script is safe to re-run.
const appUser =
    (typeof MONGO_APP_USER !== "undefined" && MONGO_APP_USER)
        ? MONGO_APP_USER
        : process.env.MONGO_APP_USER;
const appPassword =
    (typeof MONGO_APP_PASSWORD !== "undefined" && MONGO_APP_PASSWORD)
        ? MONGO_APP_PASSWORD
        : process.env.MONGO_APP_PASSWORD;

if (appUser && db.getUser(appUser) === null) {
    db.createUser({
        user: appUser,
        pwd: appPassword,
        roles: [{ role: "readWrite", db: "dms" }]
    });
}

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

if (!db.getCollectionNames().includes("departments")) {
    db.createCollection("departments");
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

// Department registry: the normalized code is unique and referenced by
// Keycloak user attributes and folder/document ACLs. The default ITDLZ
// department is seeded here on a fresh database; the backend seeds it again
// idempotently at startup, so both paths converge on the same document.
db.departments.createIndex(
    { code: 1 },
    { unique: true }
);
db.departments.updateOne(
    { code: "ITDLZ" },
    {
        $setOnInsert: {
            code: "ITDLZ",
            display_name: "IT-Dienstleistungszentrum",
            active: true,
            created_at: new Date(),
            updated_at: new Date(),
            _class: "de.hof.dms.domain.Department"
        }
    },
    { upsert: true }
);

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
print("DMS database initialized: collections, indexes, root folder (path=/), and default department (ITDLZ).");
