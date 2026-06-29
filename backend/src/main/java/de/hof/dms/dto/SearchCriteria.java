package de.hof.dms.dto;

import java.time.Instant;

/**
 * Inputs for a document search: a free-text {@code term} plus optional filters,
 * sort, and pagination. All filters are optional; a {@code null}/blank field is
 * not applied.
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
