package de.hof.dms.dto;

import de.hof.dms.domain.Folder;

/**
 * Compact folder view for list displays (subfolder listings, breadcrumbs):
 * identity, {@code name} and full {@code path}. Built from a {@link Folder} via
 * {@link #from(Folder)}.
 */
public record FolderSummary(String id, String name, String path) {

    public static FolderSummary from(Folder folder) {
        return new FolderSummary(folder.getId(), folder.getName(), folder.getPath());
    }
}
