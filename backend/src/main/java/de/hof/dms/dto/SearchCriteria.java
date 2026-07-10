package de.hof.dms.dto;

import java.time.Instant;

/**
 * Inputs for a document search: a free-text {@code term} plus optional filters,
 * sort, and pagination. All filters are optional; a {@code null}/blank field is
 * not applied.
 *
 * @param term the free-text search term
 * @param documentType optional document-type filter
 * @param department optional owning-department filter
 * @param folderId optional filter restricting hits to a folder
 * @param ocrStatus optional OCR-status filter
 * @param dateFrom optional lower bound (inclusive) on the document date
 * @param dateTo optional upper bound (inclusive) on the document date
 * @param sort optional sort specification
 * @param page the zero-based page index to return
 * @param limit the maximum number of hits per page
 */
public record SearchCriteria(
        String term,
        String documentType,
        String department,
        String folderId,
        String ocrStatus,
        Instant dateFrom,
        Instant dateTo,
        String sort,
        int page,
        int limit) {}
