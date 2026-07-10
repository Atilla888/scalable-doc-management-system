package de.hof.dms.dto;

import java.util.List;

/**
 * Aggregated payload for opening a folder: the folder itself, its breadcrumb
 * trail, its immediate subfolders and the documents it contains. Backs the
 * folder-browsing view.
 */
public record FolderViewResponse(
        FolderSummary folder,
        List<BreadcrumbEntry> breadcrumb,
        List<FolderSummary> subfolders,
        List<DocumentSummary> documents) {}
