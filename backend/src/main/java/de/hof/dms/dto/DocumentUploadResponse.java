package de.hof.dms.dto;

/**
 * Response returned after a successful document upload: the new document
 * {@code id}, its overall {@code status} and the initial {@code ocrStatus} of
 * the text-extraction job that was queued.
 */
public record DocumentUploadResponse(String id, String status, String ocrStatus) {}
