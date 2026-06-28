package de.hof.dms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "documents")
public class DocumentRecord {

    @Id
    private String id;

    private String title;

    private String description;

    @Field("document_type")
    private String documentType;

    @Field("file_name")
    private String fileName;

    @Field("content_type")
    private String contentType;

    @Field("file_size")
    private long fileSize;

    @Field("upload_date")
    private Instant uploadDate;

    @Field("uploader_id")
    private String uploaderId;

    @Field("organizational_unit")
    private String organizationalUnit;

    @Indexed(unique = true)
    @Field("eap_number")
    private String eapNumber;

    @Field("folder_id")
    private String folderId;

    @Field("gridfs_file_id")
    private String gridFsFileId;

    private FolderAcl acl;

    @Field("ocr_status")
    private String ocrStatus;

    @Field("ocr_text")
    private String ocrText;

    @Field("extraction_method")
    private String extractionMethod;

    @Field("indexing_status")
    private String indexingStatus;

    @Field("document_status")
    private String documentStatus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Instant uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    public void setOrganizationalUnit(String organizationalUnit) {
        this.organizationalUnit = organizationalUnit;
    }

    public String getEapNumber() {
        return eapNumber;
    }

    public void setEapNumber(String eapNumber) {
        this.eapNumber = eapNumber;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getGridFsFileId() {
        return gridFsFileId;
    }

    public void setGridFsFileId(String gridFsFileId) {
        this.gridFsFileId = gridFsFileId;
    }

    public FolderAcl getAcl() {
        return acl;
    }

    public void setAcl(FolderAcl acl) {
        this.acl = acl;
    }

    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public String getIndexingStatus() {
        return indexingStatus;
    }

    public void setIndexingStatus(String indexingStatus) {
        this.indexingStatus = indexingStatus;
    }

    public String getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }
}
