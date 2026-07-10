package de.hof.dms.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Persistent entity for an uploaded document, stored in the MongoDB
 * {@code documents} collection. The binary file itself lives in GridFS
 * (referenced by {@code gridFsFileId}); this record holds the metadata,
 * ownership/permission data ({@link FolderAcl}) and processing state.
 *
 * <p>Several string fields carry status/lifecycle values:
 * {@code ocrStatus} tracks the OCR/text-extraction job (e.g. pending, running,
 * done, failed), {@code extractionMethod} records how text was obtained (e.g.
 * born-digital extraction vs. OCR), {@code ocrError}/{@code retryCount} capture
 * failure details and retry attempts, {@code indexingStatus} tracks full-text
 * index state, and {@code documentStatus} reflects the overall document
 * lifecycle. {@code eapNumber} is the unique business identifier assigned to the
 * document.
 */
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

    @Field("ocr_error")
    private String ocrError;

    @Field("retry_count")
    private int retryCount;

    @Field("indexing_status")
    private String indexingStatus;

    @Field("document_status")
    private String documentStatus;

    /** Returns the document's unique identifier. */
    public String getId() {
        return id;
    }

    /** Sets the document's unique identifier. */
    public void setId(String id) {
        this.id = id;
    }

    /** Returns the human-readable document title. */
    public String getTitle() {
        return title;
    }

    /** Sets the human-readable document title. */
    public void setTitle(String title) {
        this.title = title;
    }

    /** Returns the free-text document description. */
    public String getDescription() {
        return description;
    }

    /** Sets the free-text document description. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Returns the document type/category. */
    public String getDocumentType() {
        return documentType;
    }

    /** Sets the document type/category. */
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    /** Returns the original uploaded file name. */
    public String getFileName() {
        return fileName;
    }

    /** Sets the original uploaded file name. */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** Returns the MIME content type of the stored file. */
    public String getContentType() {
        return contentType;
    }

    /** Sets the MIME content type of the stored file. */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /** Returns the file size in bytes. */
    public long getFileSize() {
        return fileSize;
    }

    /** Sets the file size in bytes. */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /** Returns the timestamp at which the document was uploaded. */
    public Instant getUploadDate() {
        return uploadDate;
    }

    /** Sets the timestamp at which the document was uploaded. */
    public void setUploadDate(Instant uploadDate) {
        this.uploadDate = uploadDate;
    }

    /** Returns the identifier of the user who uploaded the document. */
    public String getUploaderId() {
        return uploaderId;
    }

    /** Sets the identifier of the user who uploaded the document. */
    public void setUploaderId(String uploaderId) {
        this.uploaderId = uploaderId;
    }

    /** Returns the organizational unit the document belongs to. */
    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    /** Sets the organizational unit the document belongs to. */
    public void setOrganizationalUnit(String organizationalUnit) {
        this.organizationalUnit = organizationalUnit;
    }

    /** Returns the unique EAP business identifier assigned to the document. */
    public String getEapNumber() {
        return eapNumber;
    }

    /** Sets the unique EAP business identifier assigned to the document. */
    public void setEapNumber(String eapNumber) {
        this.eapNumber = eapNumber;
    }

    /** Returns the identifier of the folder containing the document. */
    public String getFolderId() {
        return folderId;
    }

    /** Sets the identifier of the folder containing the document. */
    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    /** Returns the GridFS file identifier referencing the stored binary content. */
    public String getGridFsFileId() {
        return gridFsFileId;
    }

    /** Sets the GridFS file identifier referencing the stored binary content. */
    public void setGridFsFileId(String gridFsFileId) {
        this.gridFsFileId = gridFsFileId;
    }

    /** Returns the access-control list governing this document. */
    public FolderAcl getAcl() {
        return acl;
    }

    /** Sets the access-control list governing this document. */
    public void setAcl(FolderAcl acl) {
        this.acl = acl;
    }

    /** Returns the OCR/text-extraction job status. */
    public String getOcrStatus() {
        return ocrStatus;
    }

    /** Sets the OCR/text-extraction job status. */
    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    /** Returns the extracted text content of the document. */
    public String getOcrText() {
        return ocrText;
    }

    /** Sets the extracted text content of the document. */
    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    /** Returns how the text was obtained (e.g. born-digital extraction vs. OCR). */
    public String getExtractionMethod() {
        return extractionMethod;
    }

    /** Sets how the text was obtained (e.g. born-digital extraction vs. OCR). */
    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    /** Returns the error detail recorded for a failed OCR/extraction run, if any. */
    public String getOcrError() {
        return ocrError;
    }

    /** Sets the error detail recorded for a failed OCR/extraction run. */
    public void setOcrError(String ocrError) {
        this.ocrError = ocrError;
    }

    /** Returns the number of OCR/extraction retry attempts made. */
    public int getRetryCount() {
        return retryCount;
    }

    /** Sets the number of OCR/extraction retry attempts made. */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /** Returns the full-text indexing status. */
    public String getIndexingStatus() {
        return indexingStatus;
    }

    /** Sets the full-text indexing status. */
    public void setIndexingStatus(String indexingStatus) {
        this.indexingStatus = indexingStatus;
    }

    /** Returns the overall document lifecycle status. */
    public String getDocumentStatus() {
        return documentStatus;
    }

    /** Sets the overall document lifecycle status. */
    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }
}
