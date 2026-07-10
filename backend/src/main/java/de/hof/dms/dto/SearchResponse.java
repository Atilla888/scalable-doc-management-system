package de.hof.dms.dto;

import java.util.List;

/**
 * A single page of search hits plus the metadata the UI needs to paginate.
 *
 * @param content the search hits on this page
 * @param page the zero-based index of this page
 * @param limit the maximum number of hits per page
 * @param totalElements the total number of hits across all pages
 * @param totalPages the total number of pages available
 * @param hasMore whether further pages of results exist
 */
public record SearchResponse(
        List<SearchResultEntry> content,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasMore) {}
