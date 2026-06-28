package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

public record DocumentMetadataResponse(
        String id,
        String title,
        String description,
        String documentType,
        String fileName,
        String contentType,
        long fileSize,
        Instant uploadDate,
        String uploaderId,
        String organizationalUnit,
        String eapNumber,
        String parentId,
        String ocrStatus,
        String extractionMethod,
        String indexingStatus,
        String documentStatus) {

    public static DocumentMetadataResponse from(DocumentRecord record) {
        return new DocumentMetadataResponse(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getDocumentType(),
                record.getFileName(),
                record.getContentType(),
                record.getFileSize(),
                record.getUploadDate(),
                record.getUploaderId(),
                record.getOrganizationalUnit(),
                record.getEapNumber(),
                record.getFolderId(),
                record.getOcrStatus(),
                record.getExtractionMethod(),
                record.getIndexingStatus(),
                record.getDocumentStatus());
    }
}
