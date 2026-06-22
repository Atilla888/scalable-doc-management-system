package de.hof.dms.cmis;

import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.SearchResultEntry;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds CMIS Browser-binding JSON for DMS objects and parses incoming
 * property arrays. Object types are mapped per the task: {@code cmis:folder} ↔
 * the {@code folders} collection, {@code cmis:document} ↔ the {@code documents}
 * collection. Properties are emitted in the succinct form (a flat
 * {@code propertyId → value} map under {@code "succinctProperties"}).
 */
public final class CmisObjects {

    public static final String TYPE_DOCUMENT = "cmis:document";
    public static final String TYPE_FOLDER = "cmis:folder";

    private CmisObjects() {}

    /** A {@code cmis:folder} object. */
    public static Map<String, Object> folder(Folder folder) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cmis:objectId", folder.getId());
        props.put("cmis:baseTypeId", TYPE_FOLDER);
        props.put("cmis:objectTypeId", TYPE_FOLDER);
        props.put("cmis:name", folder.getName());
        props.put("cmis:path", folder.getPath());
        props.put("cmis:parentId", folder.getParentId());
        FolderAcl acl = folder.getAcl();
        props.put("cmis:createdBy", acl != null ? acl.getOwner() : null);
        props.put("cmis:creationDate", epochMillis(folder.getCreatedAt()));
        return succinct(props);
    }

    /** A {@code cmis:document} object built from a stored record. */
    public static Map<String, Object> document(DocumentRecord record) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cmis:objectId", record.getId());
        props.put("cmis:baseTypeId", TYPE_DOCUMENT);
        props.put("cmis:objectTypeId", TYPE_DOCUMENT);
        props.put("cmis:name", record.getTitle());
        props.put("cmis:createdBy", record.getUploaderId());
        props.put("cmis:creationDate", epochMillis(record.getUploadDate()));
        props.put("cmis:contentStreamLength", record.getFileSize());
        props.put("cmis:contentStreamMimeType", record.getContentType());
        props.put("cmis:contentStreamFileName", record.getFileName());
        props.put("cmis:parentId", record.getFolderId());
        props.put("dms:eapNumber", record.getEapNumber());
        props.put("dms:documentType", record.getDocumentType());
        return succinct(props);
    }

    /** A {@code cmis:document} object built from a search hit (query results). */
    public static Map<String, Object> documentFromSearch(SearchResultEntry hit) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cmis:objectId", hit.id());
        props.put("cmis:baseTypeId", TYPE_DOCUMENT);
        props.put("cmis:objectTypeId", TYPE_DOCUMENT);
        props.put("cmis:name", hit.title());
        props.put("cmis:parentId", hit.parentFolderId());
        props.put("cmis:creationDate", epochMillis(hit.updatedAt()));
        props.put("dms:eapNumber", hit.eapNumber());
        props.put("dms:documentType", hit.documentType());
        return succinct(props);
    }

    /** Wraps an object as a child/parent entry: {@code {"object": {...}}}. */
    public static Map<String, Object> entry(Map<String, Object> object) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("object", object);
        return wrapper;
    }

    /**
     * Extracts CMIS properties from a Browser-binding request. Reads the indexed
     * arrays {@code propertyId[n]} / {@code propertyValue[n]} and, as a
     * convenience, also accepts a bare {@code cmis:name} form field.
     */
    public static Map<String, String> parseProperties(Map<String, String[]> params) {
        Map<String, String> props = new HashMap<>();
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("propertyId[") && key.endsWith("]")) {
                String index = key.substring("propertyId[".length(), key.length() - 1);
                String id = first(e.getValue());
                if (id != null && !id.isBlank()) {
                    props.put(id, first(params.get("propertyValue[" + index + "]")));
                }
            }
        }
        // Convenience fallbacks for clients that send bare DMS/CMIS fields.
        for (String direct : new String[] {"cmis:name", "dms:documentType", "dms:description"}) {
            if (!props.containsKey(direct) && params.containsKey(direct)) {
                props.put(direct, first(params.get(direct)));
            }
        }
        return props;
    }

    private static Map<String, Object> succinct(Map<String, Object> props) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("succinctProperties", props);
        return object;
    }

    private static Long epochMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private static String first(String[] values) {
        return values != null && values.length > 0 ? values[0] : null;
    }
}
