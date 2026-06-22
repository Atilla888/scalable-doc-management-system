package de.hof.dms.dto;

import de.hof.dms.domain.Folder;

public record FolderSummary(String id, String name, String path) {

    public static FolderSummary from(Folder folder) {
        return new FolderSummary(folder.getId(), folder.getName(), folder.getPath());
    }
}
