package de.hof.dms.dto;

/**
 * Response returned after a successful document upload: the new document
 * {@code id}, its overall {@code status} and the initial {@code ocrStatus} of
 * the text-extraction job that was queued.
 *
 * @param id        identifier of the newly created document
 * @param status    overall status of the created document
 * @param ocrStatus initial status of the queued OCR/text-extraction job
 */
public record DocumentUploadResponse(String id, String status, String ocrStatus) {}
