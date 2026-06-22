package de.hof.dms.dto;

/**
 * Payload for {@code POST /api/folders}.
 *
 * <p>{@code parentId} is optional — when omitted (or blank) the folder is
 * created directly under the root folder. {@code inheritFromParent} defaults
 * to {@code true} when not supplied.
 */
public record CreateFolderRequest(String name, String parentId, Boolean inheritFromParent) {}
