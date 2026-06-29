package de.hof.dms.dto;

/**
 * Payload for {@code PATCH /api/folders/{id}} — rename and/or move.
 *
 * <p>{@code name} renames the folder; {@code parentId} moves it under a new
 * parent. Either or both may be supplied; a {@code null}/blank field leaves
 * that aspect unchanged.
 */
public record UpdateFolderRequest(String name, String parentId) {}
