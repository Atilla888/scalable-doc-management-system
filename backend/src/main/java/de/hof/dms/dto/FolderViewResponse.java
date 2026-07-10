package de.hof.dms.dto;

import java.util.List;

/**
 * Aggregated payload for opening a folder: the folder itself, its breadcrumb
 * trail, its immediate subfolders and the documents it contains. Backs the
 * folder-browsing view.
 *
 * @param folder     summary of the folder currently being viewed
 * @param breadcrumb ordered path of ancestor folders from the root to this folder
 * @param subfolders immediate child folders contained in this folder
 * @param documents  documents directly contained in this folder
 */
public record FolderViewResponse(
        FolderSummary folder,
        List<BreadcrumbEntry> breadcrumb,
        List<FolderSummary> subfolders,
        List<DocumentSummary> documents) {}
