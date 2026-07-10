package de.hof.dms.dto;

/**
 * One node in a folder breadcrumb trail: the folder {@code id} and its display
 * {@code name}. Ordered lists of these describe the path from the root to the
 * current folder in folder-view responses.
 *
 * @param id   unique identifier of the folder this breadcrumb node points to
 * @param name display name of the folder shown in the breadcrumb trail
 */
public record BreadcrumbEntry(String id, String name) {}
