package de.hof.dms.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.dto.DocumentMetadataResponse;
import de.hof.dms.dto.DocumentUploadResponse;
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
    public static final String UPLOAD_STATUS = "UPLOADED";

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final GridFsTemplate gridFsTemplate;
    private final EapNumberService eapNumberService;

    public DocumentService(
            DocumentRepository documentRepository,
            FolderRepository folderRepository,
            GridFsTemplate gridFsTemplate,
            EapNumberService eapNumberService) {
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.eapNumberService = eapNumberService;
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
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required");
        }

        Folder parent =
                folderRepository
                        .findById(parentId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Parent folder not found"));

        String category = eapCategory != null && !eapCategory.isBlank() ? eapCategory.trim() : "1000";
        String eapNumber = eapNumberService.generateNext(category, user.department());

        String contentType =
                file.getContentType() != null && !file.getContentType().isBlank()
                        ? file.getContentType()
                        : "application/octet-stream";
        String fileName =
                file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                        ? file.getOriginalFilename()
                        : "upload.bin";

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
        record.setOcrStatus(resolveOcrStatus(contentType, documentType));
        record.setIndexingStatus(INDEXING_PENDING);
        record.setDocumentStatus(STATUS_ACTIVE);

        DocumentRecord saved = documentRepository.save(record);
        return new DocumentUploadResponse(saved.getId(), UPLOAD_STATUS, saved.getOcrStatus());
    }

    public DocumentMetadataResponse getMetadata(String id) {
        DocumentRecord record = findActiveDocument(id);
        return DocumentMetadataResponse.from(record);
    }

    public DownloadPayload download(String id) {
        DocumentRecord record = findActiveDocument(id);
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

    public void delete(String id) {
        DocumentRecord record = findActiveDocument(id);
        record.setDocumentStatus(STATUS_DELETED);
        documentRepository.save(record);
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

    private FolderAcl buildDocumentAcl(CurrentUser user, Folder parent, boolean inheritFromParent) {
        FolderAcl acl = new FolderAcl();
        acl.setOwner(user.username());
        acl.setOwnerDepartment(user.department());
        acl.setAllowedUserIds(new ArrayList<>(List.of(user.username())));
        acl.setAllowedRoles(new ArrayList<>(user.roles()));
        if (user.department() != null && !user.department().isBlank()) {
            acl.setAllowedDepartments(new ArrayList<>(List.of(user.department())));
        } else {
            acl.setAllowedDepartments(new ArrayList<>());
        }

        FolderAccess access = new FolderAccess();
        access.setRead(true);
        access.setCreate(false);
        access.setUpdate(true);
        access.setDelete(true);
        access.setManagePermissions(false);
        acl.setAccess(access);
        acl.setInheritFromParent(false);

        FolderAcl parentAcl = parent.getAcl();
        if (inheritFromParent && parentAcl != null) {
            if (parentAcl.getAllowedRoles() != null) {
                acl.setAllowedRoles(new ArrayList<>(parentAcl.getAllowedRoles()));
            }
            if (parentAcl.getAllowedDepartments() != null) {
                acl.setAllowedDepartments(new ArrayList<>(parentAcl.getAllowedDepartments()));
            }
            if (parentAcl.getAccess() != null) {
                acl.setAccess(copyAccess(parentAcl.getAccess()));
            }
        }

        return acl;
    }

    private FolderAccess copyAccess(FolderAccess source) {
        FolderAccess access = new FolderAccess();
        access.setRead(source.isRead());
        access.setCreate(source.isCreate());
        access.setUpdate(source.isUpdate());
        access.setDelete(source.isDelete());
        access.setManagePermissions(source.isManagePermissions());
        return access;
    }

    private org.bson.Document metadataFor(CurrentUser user, String title) {
        org.bson.Document metadata = new org.bson.Document();
        metadata.put("uploader", user.username());
        metadata.put("title", title);
        return metadata;
    }

    public record DownloadPayload(Resource resource, String contentType, String fileName) {}
}
