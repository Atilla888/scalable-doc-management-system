package de.hof.dms.dto;

/**
 * Request payload for editing a document's descriptive metadata (title,
 * description and document type) via the metadata-update endpoint.
 */
public record MetadataUpdateRequest(String title, String description, String documentType) {}
