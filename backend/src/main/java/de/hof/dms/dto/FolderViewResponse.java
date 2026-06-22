package de.hof.dms.dto;

import java.util.List;

public record FolderViewResponse(
        FolderSummary folder,
        List<BreadcrumbEntry> breadcrumb,
        List<FolderSummary> subfolders,
        List<DocumentSummary> documents) {}
