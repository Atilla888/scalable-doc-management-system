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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {

    private DepartmentRepository departmentRepository;
    private FolderRepository folderRepository;
    private DocumentRepository documentRepository;
    private KeycloakAdminService keycloakAdminService;
    private DepartmentService service;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(DepartmentRepository.class);
        folderRepository = mock(FolderRepository.class);
        documentRepository = mock(DocumentRepository.class);
        keycloakAdminService = mock(KeycloakAdminService.class);
        service =
                new DepartmentService(
                        departmentRepository,
                        folderRepository,
                        documentRepository,
                        keycloakAdminService);
        when(departmentRepository.insert(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Department department(String code, boolean active) {
        Department department = new Department();
        department.setId("id-" + code);
        department.setCode(code);
        department.setDisplayName(code + " department");
        department.setActive(active);
        department.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        department.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return department;
    }

    @Test
    void seedingIsIdempotent() {
        when(departmentRepository.existsByCode("ITDLZ")).thenReturn(false, true);

        service.ensureDefaultDepartment();
        service.ensureDefaultDepartment();

        verify(departmentRepository, times(1)).insert(any(Department.class));
    }

    @Test
    void seedingToleratesConcurrentInsert() {
        when(departmentRepository.existsByCode("ITDLZ")).thenReturn(false);
        when(departmentRepository.insert(any(Department.class)))
                .thenThrow(new DuplicateKeyException("code exists"));

        assertThatCode(() -> service.ensureDefaultDepartment()).doesNotThrowAnyException();
    }

    @Test
    void createNormalizesCodeAndDefaultsDisplayName() {
        when(departmentRepository.existsByCode("ITDLZ2")).thenReturn(false);

        DepartmentResponse created =
                service.createDepartment(new CreateDepartmentRequest("  itdlz2 ", "  "));

        assertThat(created.code()).isEqualTo("ITDLZ2");
        assertThat(created.displayName()).isEqualTo("ITDLZ2");
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(departmentRepository.existsByCode("ITDLZ")).thenReturn(true);

        assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentRequest("itdlz", null)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(departmentRepository, never()).insert(any(Department.class));
    }

    @Test
    void createTranslatesDuplicateKeyRaceIntoConflict() {
        when(departmentRepository.existsByCode("FIN")).thenReturn(false);
        when(departmentRepository.insert(any(Department.class)))
                .thenThrow(new DuplicateKeyException("code exists"));

        assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentRequest("FIN", null)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createRejectsMissingOrMalformedCode() {
        for (String code : new String[] {null, "  ", "A", "IT DLZ", "it/dlz", "_ITDLZ"}) {
            assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentRequest(code, null)))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void updateChangesDisplayNameAndActiveButNotCode() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));

        DepartmentResponse updated =
                service.updateDepartment("itdlz", new UpdateDepartmentRequest("IT Service Center", false));

        assertThat(updated.code()).isEqualTo("ITDLZ");
        assertThat(updated.displayName()).isEqualTo("IT Service Center");
        assertThat(updated.active()).isFalse();
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void updateRejectsBlankDisplayName() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));

        assertThatThrownBy(() -> service.updateDepartment("ITDLZ", new UpdateDepartmentRequest(" ", null)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateFailsForUnknownDepartment() {
        when(departmentRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDepartment("NOPE", new UpdateDepartmentRequest("x", null)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteIsRejectedWhileUsersReferenceTheDepartment() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));
        when(keycloakAdminService.hasUsersWithDepartment("ITDLZ")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("deactivate");
        verify(departmentRepository, never()).delete(any(Department.class));
    }

    @Test
    void deleteIsRejectedWhileFoldersReferenceTheDepartment() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));
        when(keycloakAdminService.hasUsersWithDepartment("ITDLZ")).thenReturn(false);
        when(folderRepository.existsByAclAllowedDepartments("ITDLZ")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(departmentRepository, never()).delete(any(Department.class));
    }

    @Test
    void deleteIsRejectedWhileDocumentsReferenceTheDepartment() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));
        when(keycloakAdminService.hasUsersWithDepartment("ITDLZ")).thenReturn(false);
        when(documentRepository.existsByAclOwnerDepartment("ITDLZ")).thenReturn(true);

        assertThatThrownBy(() -> service.deleteDepartment("ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(departmentRepository, never()).delete(any(Department.class));
    }

    @Test
    void deleteRemovesUnreferencedDepartment() {
        Department department = department("OLD", false);
        when(departmentRepository.findByCode("OLD")).thenReturn(Optional.of(department));
        when(keycloakAdminService.hasUsersWithDepartment("OLD")).thenReturn(false);

        service.deleteDepartment("OLD");

        verify(departmentRepository).delete(department);
    }

    @Test
    void assignRejectsUnknownDepartment() {
        when(departmentRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.assignUserDepartment("user-1", new AssignDepartmentRequest("nope")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(keycloakAdminService, never()).updateUserDepartment(any(), any());
    }

    @Test
    void assignRejectsInactiveDepartment() {
        when(departmentRepository.findByCode("OLD"))
                .thenReturn(Optional.of(department("OLD", false)));

        assertThatThrownBy(
                        () -> service.assignUserDepartment("user-1", new AssignDepartmentRequest("OLD")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(keycloakAdminService, never()).updateUserDepartment(any(), any());
    }

    @Test
    void assignFailsForUnknownUser() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));
        when(keycloakAdminService.updateUserDepartment("ghost", "ITDLZ")).thenReturn(false);

        assertThatThrownBy(
                        () -> service.assignUserDepartment("ghost", new AssignDepartmentRequest("ITDLZ")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void assignWritesActiveDepartmentToKeycloak() {
        when(departmentRepository.findByCode("ITDLZ"))
                .thenReturn(Optional.of(department("ITDLZ", true)));
        when(keycloakAdminService.updateUserDepartment("user-1", "ITDLZ")).thenReturn(true);

        service.assignUserDepartment("user-1", new AssignDepartmentRequest(" itdlz "));

        verify(keycloakAdminService).updateUserDepartment("user-1", "ITDLZ");
    }

    @Test
    void assignWithBlankDepartmentRemovesTheAttribute() {
        when(keycloakAdminService.updateUserDepartment("user-1", null)).thenReturn(true);

        service.assignUserDepartment("user-1", new AssignDepartmentRequest("  "));

        verify(keycloakAdminService).updateUserDepartment("user-1", null);
    }
}
