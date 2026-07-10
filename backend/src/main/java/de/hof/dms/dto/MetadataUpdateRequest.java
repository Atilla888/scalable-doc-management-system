package de.hof.dms.dto;

/**
 * Request payload for editing a document's descriptive metadata (title,
 * description and document type) via the metadata-update endpoint.
 *
 * @param title        new display title for the document
 * @param description  new free-text description of the document
 * @param documentType new document-type classification value
 */
public record MetadataUpdateRequest(String title, String description, String documentType) {}
