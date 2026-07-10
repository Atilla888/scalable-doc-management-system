package de.hof.dms.dto;

/**
 * Payload for {@code POST /api/folders}.
 *
 * <p>{@code parentId} is optional — when omitted (or blank) the folder is
 * created directly under the root folder. {@code inheritFromParent} defaults
 * to {@code true} when not supplied.
 *
 * @param name the name for the new folder
 * @param parentId optional id of the parent folder; blank/omitted means root
 * @param inheritFromParent whether the folder inherits its parent's ACL; defaults to {@code true}
 */
public record CreateFolderRequest(String name, String parentId, Boolean inheritFromParent) {}
