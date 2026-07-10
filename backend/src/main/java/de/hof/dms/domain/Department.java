package de.hof.dms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persistent entity for a department, stored in the MongoDB
 * {@code departments} collection. This collection is the application's
 * department registry; the normalized {@code code} (e.g. {@code ITDLZ}) is the
 * value referenced by Keycloak user attributes, JWT {@code department} claims,
 * and {@link FolderAcl} department lists. The code is immutable after
 * creation because those references are plain strings; departments that are no
 * longer used are deactivated rather than renamed or deleted.
 */
@Document(collection = "departments")
public class Department {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    @Field("display_name")
    private String displayName;

    private boolean active;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    /** Returns the department's unique identifier. */
    public String getId() {
        return id;
    }

    /** Sets the department's unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Returns the normalized, unique department code (immutable after creation). */
    public String getCode() {
        return code;
    }

    /** Sets the normalized, unique department code. */
    public void setCode(String code) {
        this.code = code;
    }

    /** Returns the human-readable department name. */
    public String getDisplayName() {
        return displayName;
    }

    /** Sets the human-readable department name. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Returns whether the department may currently be assigned to users. */
    public boolean isActive() {
        return active;
    }

    /** Sets whether the department may currently be assigned to users. */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** Returns the timestamp at which the department was created. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sets the timestamp at which the department was created. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Returns the timestamp of the last modification. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Sets the timestamp of the last modification. */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
