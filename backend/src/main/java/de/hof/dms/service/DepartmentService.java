package de.hof.dms.service;

import de.hof.dms.domain.Department;
import de.hof.dms.dto.AssignDepartmentRequest;
import de.hof.dms.dto.CreateDepartmentRequest;
import de.hof.dms.dto.DepartmentResponse;
import de.hof.dms.dto.UpdateDepartmentRequest;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DepartmentRepository;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Manages the application's department registry (MongoDB {@code departments}
 * collection) and the assignment of departments to Keycloak users.
 *
 * <p>The normalized department code is the value carried by Keycloak user
 * attributes, JWT {@code department} claims, and folder/document ACLs, so it
 * is immutable after creation and a department cannot be deleted while any of
 * those still reference it — deactivation is the supported alternative. The
 * default department {@code ITDLZ} is seeded idempotently at startup.
 */
@Service
@Profile("!no-mongo")
public class DepartmentService {

    /** The department the demo realm ships with; seeded on startup when missing. */
    public static final String DEFAULT_DEPARTMENT_CODE = "ITDLZ";
    static final String DEFAULT_DEPARTMENT_DISPLAY_NAME = "IT-Dienstleistungszentrum";

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    /** Uppercase alphanumeric plus {@code _} and {@code -}, 2–32 chars, letter/digit first. */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");

    private final DepartmentRepository departmentRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final KeycloakAdminService keycloakAdminService;

    /** Creates the service with the registry repository and the collaborators used for reference checks. */
    public DepartmentService(
            DepartmentRepository departmentRepository,
            FolderRepository folderRepository,
            DocumentRepository documentRepository,
            KeycloakAdminService keycloakAdminService) {
        this.departmentRepository = departmentRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    /**
     * Seeds the default {@code ITDLZ} department once the application is up.
     * Failures are logged instead of aborting startup (e.g. while MongoDB is
     * still unavailable); {@code scripts/mongo-init.js} seeds the same
     * department on a fresh database, so both paths converge.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultDepartmentOnStartup() {
        try {
            ensureDefaultDepartment();
        } catch (DataAccessException exception) {
            log.warn(
                    "Could not seed default department '{}' at startup: {}",
                    DEFAULT_DEPARTMENT_CODE,
                    exception.getMessage());
        }
    }

    /** Inserts the default department if it does not exist yet. Safe to call repeatedly. */
    public void ensureDefaultDepartment() {
        if (departmentRepository.existsByCode(DEFAULT_DEPARTMENT_CODE)) {
            return;
        }
        try {
            departmentRepository.insert(
                    newDepartment(DEFAULT_DEPARTMENT_CODE, DEFAULT_DEPARTMENT_DISPLAY_NAME));
            log.info("Seeded default department '{}'", DEFAULT_DEPARTMENT_CODE);
        } catch (DuplicateKeyException ignored) {
            // Another replica or mongo-init.js seeded it concurrently — the goal state holds.
        }
    }

    /** Lists all departments in the registry, ordered by code. */
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAllByOrderByCodeAsc().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    /**
     * Creates a new department from the request, normalizing the code and
     * rejecting duplicates.
     *
     * @throws ApiException 400 for a missing or malformed code, 409 for a duplicate
     */
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        String code = normalizeCode(request == null ? null : request.code());
        String displayName =
                request.displayName() == null || request.displayName().isBlank()
                        ? code
                        : request.displayName().trim();
        if (departmentRepository.existsByCode(code)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A department with code '" + code + "' already exists");
        }
        try {
            return DepartmentResponse.from(departmentRepository.insert(newDepartment(code, displayName)));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A department with code '" + code + "' already exists");
        }
    }

    /**
     * Updates a department's display name and/or active flag. The code itself is
     * immutable because documents, folders, and ACLs reference it by value.
     *
     * @throws ApiException 400 for a blank display name, 404 when the code is unknown
     */
    public DepartmentResponse updateDepartment(String code, UpdateDepartmentRequest request) {
        Department department = requireDepartment(code);
        boolean changed = false;
        if (request != null && request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The display name must not be blank");
            }
            department.setDisplayName(displayName);
            changed = true;
        }
        if (request != null && request.active() != null) {
            department.setActive(request.active());
            changed = true;
        }
        if (changed) {
            department.setUpdatedAt(Instant.now());
            department = departmentRepository.save(department);
        }
        return DepartmentResponse.from(department);
    }

    /**
     * Deletes a department that nothing references anymore. While users, folders,
     * documents, or ACLs still carry the code, deletion is rejected and the
     * department should be deactivated instead.
     *
     * @throws ApiException 404 when the code is unknown, 409 while still referenced
     */
    public void deleteDepartment(String code) {
        Department department = requireDepartment(code);
        String normalized = department.getCode();
        if (keycloakAdminService.hasUsersWithDepartment(normalized)) {
            rejectDelete(normalized, "users are still assigned to it");
        }
        if (folderRepository.existsByAclOwnerDepartment(normalized)
                || folderRepository.existsByAclAllowedDepartments(normalized)) {
            rejectDelete(normalized, "folders or folder ACLs still reference it");
        }
        if (documentRepository.existsByAclOwnerDepartment(normalized)
                || documentRepository.existsByAclAllowedDepartments(normalized)
                || documentRepository.existsByOrganizationalUnit(normalized)) {
            rejectDelete(normalized, "documents or document ACLs still reference it");
        }
        departmentRepository.delete(department);
    }

    /**
     * Assigns a department to a Keycloak user (or removes the assignment for a
     * blank value) by updating the user's {@code department} attribute. Only
     * existing, active departments may be assigned.
     *
     * @throws ApiException 400 for an unknown department, 409 for an inactive one,
     *     404 when the Keycloak user does not exist
     */
    public void assignUserDepartment(String userId, AssignDepartmentRequest request) {
        String requested = request == null ? null : request.department();
        String code = null;
        if (requested != null && !requested.isBlank()) {
            code = normalizeCode(requested);
            Department department =
                    departmentRepository
                            .findByCode(code)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Unknown department code — create the department first"));
            if (!department.isActive()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Department '" + department.getCode() + "' is inactive and cannot be assigned");
            }
        }
        if (!keycloakAdminService.updateUserDepartment(userId, code)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No Keycloak user with this id exists");
        }
    }

    /** Normalizes and validates a department code (trim, upper-case, charset check). */
    private static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The department code is required");
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Department codes must be 2-32 characters of A-Z, 0-9, '_' or '-' and start"
                            + " with a letter or digit");
        }
        return code;
    }

    /** Loads a department by (normalized) code or fails with 404. */
    private Department requireDepartment(String code) {
        return departmentRepository
                .findByCode(normalizeCode(code))
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "No department with this code exists"));
    }

    /** Throws the standard 409 for delete attempts on a referenced department. */
    private static void rejectDelete(String code, String reason) {
        throw new ApiException(
                HttpStatus.CONFLICT,
                "Department '" + code + "' cannot be deleted because " + reason
                        + "; deactivate it instead");
    }

    /** Builds a new active department entity with both timestamps set to now. */
    private static Department newDepartment(String code, String displayName) {
        Department department = new Department();
        department.setCode(code);
        department.setDisplayName(displayName);
        department.setActive(true);
        Instant now = Instant.now();
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        return department;
    }
}
