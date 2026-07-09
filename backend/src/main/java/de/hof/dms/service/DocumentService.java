package de.hof.dms.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.dto.AclDto;
import de.hof.dms.dto.DocumentMetadataResponse;
import de.hof.dms.dto.DocumentUploadResponse;
import de.hof.dms.dto.MetadataUpdateRequest;
import de.hof.dms.dto.PermissionsResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("!no-mongo")
public class DocumentService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DELETED = "deleted";
    public static final String OCR_NOT_REQUIRED = "not_required";
    public static final String OCR_PENDING = "pending";
    public static final String INDEXING_PENDING = "pending";
    public static final String INDEXING_INDEXED = "indexed";
    public static final String UPLOAD_STATUS = "UPLOADED";

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final GridFsTemplate gridFsTemplate;
    private final EapNumberService eapNumberService;
    private final PermissionService permissionService;
    private final UploadValidationService uploadValidationService;

    public DocumentService(
            DocumentRepository documentRepository,
            FolderRepository folderRepository,
            GridFsTemplate gridFsTemplate,
            EapNumberService eapNumberService,
            PermissionService permissionService,
            UploadValidationService uploadValidationService) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.eapNumberService = eapNumberService;
        this.permissionService = permissionService;
        this.uploadValidationService = uploadValidationService;
    }

    public DocumentUploadResponse upload(
            MultipartFile file,
            String title,
            String documentType,
            String parentId,
            String description,
            String eapCategory,
            boolean inheritFromParent,
            CurrentUser user) {

        validateRequiredMetadata(title, documentType, parentId);
        UploadValidationService.ValidatedUpload validatedFile =
                uploadValidationService.validate(file);

        Folder parent =
                folderRepository
                        .findById(parentId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Parent folder not found"));

        permissionService.requireFolder(user, parent, PermissionService.Action.CREATE);

        String category = eapCategory != null && !eapCategory.isBlank() ? eapCategory.trim() : "1000";
        String eapNumber = eapNumberService.generateNext(category, user.department());

        String contentType = validatedFile.contentType();
        String fileName = validatedFile.fileName();

        ObjectId gridFsId;
        try {
            gridFsId =
                    gridFsTemplate.store(
                            file.getInputStream(), fileName, contentType, metadataFor(user, title));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        }

        DocumentRecord record = new DocumentRecord();
        record.setTitle(title.trim());
        record.setDescription(description != null ? description.trim() : null);
        record.setDocumentType(documentType.trim());
        record.setFileName(fileName);
        record.setContentType(contentType);
        record.setFileSize(file.getSize());
        record.setUploadDate(Instant.now());
        record.setUploaderId(user.username());
        record.setOrganizationalUnit(user.department());
        record.setEapNumber(eapNumber);
        record.setFolderId(parentId);
        record.setGridFsFileId(gridFsId.toHexString());
        record.setAcl(buildDocumentAcl(user, parent, inheritFromParent));
        String ocrStatus = resolveOcrStatus(contentType, documentType);
        record.setOcrStatus(ocrStatus);
        // Documents that need no OCR carry no text to extract, so they are
        // searchable by their metadata immediately; documents awaiting OCR are
        // marked indexed by the worker once their text is available.
        record.setIndexingStatus(
                OCR_NOT_REQUIRED.equals(ocrStatus) ? INDEXING_INDEXED : INDEXING_PENDING);
        record.setDocumentStatus(STATUS_ACTIVE);

        DocumentRecord saved = documentRepository.save(record);
        return new DocumentUploadResponse(saved.getId(), UPLOAD_STATUS, saved.getOcrStatus());
    }

    public DocumentMetadataResponse getMetadata(String id, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.READ);
        return DocumentMetadataResponse.from(record);
    }

    public DownloadPayload download(String id, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.READ);
        GridFSFile gridFile =
                gridFsTemplate.findOne(
                        Query.query(Criteria.where("_id").is(new ObjectId(record.getGridFsFileId()))));
        if (gridFile == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Stored file not found");
        }

        Resource resource = gridFsTemplate.getResource(gridFile);
        if (!(resource instanceof GridFsResource gridFsResource) || !gridFsResource.exists()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Stored file not found");
        }

        return new DownloadPayload(gridFsResource, record.getContentType(), record.getFileName());
    }

    public void delete(String id, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.DELETE);
        record.setDocumentStatus(STATUS_DELETED);
        documentRepository.save(record);
    }

    /**
     * Replaces the binary content of an existing document (CMIS setContentStream).
     * Requires <b>update</b> permission. The previous GridFS blob is removed and
     * the stored file name, content type, and size are refreshed.
     */
    public DocumentMetadataResponse setContent(String id, MultipartFile file, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.UPDATE);
        UploadValidationService.ValidatedUpload validatedFile =
                uploadValidationService.validate(file);
        String contentType = validatedFile.contentType();
        String fileName = validatedFile.fileName();

        ObjectId gridFsId;
        try {
            gridFsId =
                    gridFsTemplate.store(
                            file.getInputStream(),
                            fileName,
                            contentType,
                            metadataFor(user, record.getTitle()));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file");
        }

        // Drop the old blob only after the new one is stored.
        if (record.getGridFsFileId() != null) {
            gridFsTemplate.delete(
                    Query.query(Criteria.where("_id").is(new ObjectId(record.getGridFsFileId()))));
        }

        record.setGridFsFileId(gridFsId.toHexString());
        record.setContentType(contentType);
        record.setFileName(fileName);
        record.setFileSize(file.getSize());
        documentRepository.save(record);
        return DocumentMetadataResponse.from(record);
    }

    public DocumentMetadataResponse updateMetadata(
            String id, MetadataUpdateRequest request, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.UPDATE);

        if (request != null) {
            if (request.title() != null && !request.title().isBlank()) {
                record.setTitle(request.title().trim());
            }
            if (request.description() != null) {
                record.setDescription(request.description().trim());
            }
            if (request.documentType() != null && !request.documentType().isBlank()) {
                record.setDocumentType(request.documentType().trim());
            }
        }
        DocumentRecord saved = documentRepository.save(record);
        return DocumentMetadataResponse.from(saved);
    }

    public PermissionsResponse getPermissions(String id, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(user, record, PermissionService.Action.READ);

        var effective =
                new PermissionsResponse.EffectivePermissions(
                        permissionService.canDocument(user, record, PermissionService.Action.READ),
                        permissionService.canDocument(user, record, PermissionService.Action.CREATE),
                        permissionService.canDocument(user, record, PermissionService.Action.UPDATE),
                        permissionService.canDocument(user, record, PermissionService.Action.DELETE),
                        permissionService.canDocument(
                                user, record, PermissionService.Action.MANAGE_PERMISSIONS));

        // Full ACL only for admins or users with managePermissions on the document.
        AclDto acl =
                permissionService.canManageDocument(user, record)
                        ? AclDto.from(record.getAcl())
                        : null;
        return new PermissionsResponse(effective, acl);
    }

    public AclDto updatePermissions(String id, AclDto request, CurrentUser user) {
        DocumentRecord record = findActiveDocument(id);
        permissionService.requireDocument(
                user, record, PermissionService.Action.MANAGE_PERMISSIONS);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACL payload is required");
        }
        record.setAcl(request.toAcl());
        DocumentRecord saved = documentRepository.save(record);
        return AclDto.from(saved.getAcl());
    }

    private DocumentRecord findActiveDocument(String id) {
        DocumentRecord record =
                documentRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (STATUS_DELETED.equals(record.getDocumentStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return record;
    }

    private void validateRequiredMetadata(String title, String documentType, String parentId) {
        if (title == null || title.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "documentType is required");
        }
        if (parentId == null || parentId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "parentId is required");
        }
    }

    static String resolveOcrStatus(String contentType, String documentType) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return OCR_PENDING;
        }
        if ("scan".equalsIgnoreCase(documentType)) {
            return OCR_PENDING;
        }
        if ("application/pdf".equalsIgnoreCase(contentType)) {
            return OCR_PENDING;
        }
        return OCR_NOT_REQUIRED;
    }

    /**
     * Default ACL for a freshly uploaded document. It is private to its owner
     * (the uploader) — owners always get full access via the RBAC owner rule,
     * and admins/department managers via their role rules. When
     * {@code inheritFromParent} is true, the resolver additionally walks up to
     * the parent folder's ACL, so colleagues who can read the folder can read
     * the document. Broader sharing is granted explicitly via
     * PUT /api/documents/{id}/permissions.
     */
    private FolderAcl buildDocumentAcl(CurrentUser user, Folder parent, boolean inheritFromParent) {
        FolderAcl acl = new FolderAcl();
        acl.setOwner(user.username());
        acl.setOwnerDepartment(user.department());
        acl.setAllowedUserIds(new ArrayList<>(List.of(user.username())));
        acl.setAllowedRoles(new ArrayList<>());
        acl.setAllowedDepartments(new ArrayList<>());

        FolderAccess access = new FolderAccess();
        access.setRead(true);
        access.setCreate(false);
        access.setUpdate(true);
        access.setDelete(true);
        access.setManagePermissions(true);
        acl.setAccess(access);

        acl.setInheritFromParent(inheritFromParent);
        return acl;
    }

    private org.bson.Document metadataFor(CurrentUser user, String title) {
        org.bson.Document metadata = new org.bson.Document();
        metadata.put("uploader", user.username());
        metadata.put("title", title);
        return metadata;
    }

    public record DownloadPayload(Resource resource, String contentType, String fileName) {}
}
