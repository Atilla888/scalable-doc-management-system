package de.hof.dms.dto;

import de.hof.dms.domain.Folder;

/**
 * Compact folder view for list displays (subfolder listings, breadcrumbs):
 * identity, {@code name} and full {@code path}. Built from a {@link Folder} via
 * {@link #from(Folder)}.
 *
 * @param id   unique identifier of the folder
 * @param name display name of the folder
 * @param path full hierarchical path of the folder
 */
public record FolderSummary(String id, String name, String path) {

    /**
     * Builds a {@link FolderSummary} from a {@link Folder} domain entity.
     *
     * @param folder the folder entity to summarize
     * @return a summary exposing the folder's id, name and path
     */
    public static FolderSummary from(Folder folder) {
        return new FolderSummary(folder.getId(), folder.getName(), folder.getPath());
    }
}
