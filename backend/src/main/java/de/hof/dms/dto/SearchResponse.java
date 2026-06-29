package de.hof.dms.dto;

import java.util.List;

/** A single page of search hits plus the metadata the UI needs to paginate. */
public record SearchResponse(
        List<SearchResultEntry> content,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasMore) {}
