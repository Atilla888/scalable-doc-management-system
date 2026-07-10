package de.hof.dms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persistent entity for a folder in the document hierarchy, stored in the
 * MongoDB {@code folders} collection. Folders form a tree via {@code parentId}
 * and cache their full {@code path}; each carries its own access-control list
 * ({@link FolderAcl}) governing who may read or modify it and its contents.
 */
@Document(collection = "folders")
public class Folder {

    @Id
    private String id;

    private String name;

    private String path;

    @Field("parent_id")
    private String parentId;

    private FolderAcl acl;

    @Field("created_at")
    private Instant createdAt;

    /** Returns the folder's unique identifier. */
    public String getId() {
        return id;
    }

    /** Sets the folder's unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Returns the folder's display name. */
    public String getName() {
        return name;
    }

    /** Sets the folder's display name. */
    public void setName(String name) {
        this.name = name;
    }

    /** Returns the cached full materialized path of the folder. */
    public String getPath() {
        return path;
    }

    /** Sets the cached full materialized path of the folder. */
    public void setPath(String path) {
        this.path = path;
    }

    /** Returns the identifier of the parent folder, or {@code null} for the root. */
    public String getParentId() {
        return parentId;
    }

    /** Sets the identifier of the parent folder. */
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    /** Returns the access-control list governing this folder. */
    public FolderAcl getAcl() {
        return acl;
    }

    /** Sets the access-control list governing this folder. */
    public void setAcl(FolderAcl acl) {
        this.acl = acl;
    }

    /** Returns the timestamp at which the folder was created. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sets the timestamp at which the folder was created. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
