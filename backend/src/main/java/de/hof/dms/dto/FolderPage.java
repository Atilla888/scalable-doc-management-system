package de.hof.dms.dto;

import java.util.List;

/** A single page of folder listings. */
public record FolderPage(
        List<FolderSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
