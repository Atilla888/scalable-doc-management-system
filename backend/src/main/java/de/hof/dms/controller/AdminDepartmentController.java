package de.hof.dms.controller;

import de.hof.dms.dto.AssignDepartmentRequest;
import de.hof.dms.dto.CreateDepartmentRequest;
import de.hof.dms.dto.DepartmentResponse;
import de.hof.dms.dto.UpdateDepartmentRequest;
import de.hof.dms.service.DepartmentService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST endpoints for the department registry and for assigning
 * departments to users, under {@code /api/admin}.
 *
 * <p>Access is restricted to users with the {@code dms_admin} realm role via the URL-based
 * authorization rules in {@code SecurityConfig}. Only active when a MongoDB backend is
 * present (profile {@code !no-mongo}).
 */
@RestController
@RequestMapping("/api/admin")
@Profile("!no-mongo")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    /**
     * Creates the controller with the department service it delegates to.
     *
     * @param departmentService the service managing the department registry
     */
    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * Lists all departments in the registry, ordered by code.
     *
     * @return the department list
     */
    @GetMapping("/departments")
    public List<DepartmentResponse> list() {
        return departmentService.listDepartments();
    }

    /**
     * Creates a new department, returning HTTP 201 on success. The code is
     * normalized and must be unique.
     *
     * @param request the creation payload (code, optional display name)
     * @return a 201 response containing the created department
     */
    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> create(@RequestBody CreateDepartmentRequest request) {
        DepartmentResponse created = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates a department's display name and/or active flag. The code itself is
     * immutable.
     *
     * @param code the department code from the path
     * @param request the fields to change (nulls keep current values)
     * @return the updated department
     */
    @PutMapping("/departments/{code}")
    public DepartmentResponse update(
            @PathVariable("code") String code, @RequestBody UpdateDepartmentRequest request) {
        return departmentService.updateDepartment(code, request);
    }

    /**
     * Deletes a department that is no longer referenced by users, folders,
     * documents, or ACLs; otherwise responds 409 (deactivate instead).
     *
     * @param code the department code from the path
     * @return a 204 response on success
     */
    @DeleteMapping("/departments/{code}")
    public ResponseEntity<Void> delete(@PathVariable("code") String code) {
        departmentService.deleteDepartment(code);
        return ResponseEntity.noContent().build();
    }

    /**
     * Assigns a department to a Keycloak user or removes the assignment (blank
     * department). Only existing, active departments may be assigned; the change
     * is written to the user's Keycloak {@code department} attribute.
     *
     * @param userId the Keycloak user id from the path
     * @param request the assignment payload
     * @return a 204 response on success
     */
    @PutMapping("/users/{userId}/department")
    public ResponseEntity<Void> assignUserDepartment(
            @PathVariable("userId") String userId, @RequestBody AssignDepartmentRequest request) {
        departmentService.assignUserDepartment(userId, request);
        return ResponseEntity.noContent().build();
    }
}
