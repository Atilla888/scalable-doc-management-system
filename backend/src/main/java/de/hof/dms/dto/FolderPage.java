package de.hof.dms.dto;

import java.util.List;

/**
 * A single page of folder listings.
 *
 * @param content the folder summaries on this page
 * @param page the zero-based index of this page
 * @param size the maximum number of items per page
 * @param totalElements the total number of folders across all pages
 * @param totalPages the total number of pages available
 */
public record FolderPage(
        List<FolderSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
