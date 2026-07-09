package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.SearchCriteria;
import de.hof.dms.dto.SearchResponse;
import de.hof.dms.dto.SearchResultEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Document search with RBAC enforced at query time.
 *
 * <p>Every query combines, in a single MongoDB request (never post-filtered): a
 * case-insensitive substring match across the document's title, EAP number,
 * description, and extracted OCR text; an active-status gate; and a mandatory
 * per-user permission predicate built from the caller's token. Substring
 * matching (rather than {@code $text}) means partial titles are found and search
 * does not depend on the presence of the text index. Results are mapped to
 * {@link SearchResultEntry}, so ACL fields and raw OCR text never reach the client.
 */
@Service
@Profile("!no-mongo")
public class SearchService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final MongoTemplate mongoTemplate;

    public SearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Simple keyword search returning just the hits (used by the CMIS query path). */
    public List<SearchResultEntry> search(String term, CurrentUser user, int page, int limit) {
        return query(
                        new SearchCriteria(term, null, null, null, null, null, null, null, page, limit),
                        user)
                .content();
    }

    /**
     * Paginated, filtered, sorted document search with RBAC enforced at query
     * time. The substring term match, the active-status gate, the mandatory
     * per-user permission predicate, and every optional filter are combined into
     * a single MongoDB query — results are never post-filtered, so pagination
     * totals stay correct and restricted documents never leak.
     */
    public SearchResponse query(SearchCriteria criteria, CurrentUser user) {
        int limit = Math.min(Math.max(criteria.limit(), 1), MAX_LIMIT);
        int page = Math.max(criteria.page(), 0);

        boolean hasTerm = criteria.term() != null && !criteria.term().isBlank();
        if (user == null || (!hasTerm && !hasAnyFilter(criteria))) {
            // Nothing to search on → don't dump the whole archive.
            return new SearchResponse(List.of(), page, limit, 0, 0, false);
        }

        List<Criteria> and = new ArrayList<>();
        // Only active documents are searchable (deleted ones never surface).
        and.add(Criteria.where("document_status").is(DocumentService.STATUS_ACTIVE));
        // Mandatory RBAC predicate, applied in the same query — never post-filtered.
        Criteria permission = permissionFilter(user);
        if (permission != null) {
            and.add(permission);
        }
        if (hasTerm) {
            // Case-insensitive substring match across metadata and extracted text, so
            // partial titles ("Fin" → "Finance"), EAP numbers, and OCR content all hit.
            // Independent of the $text index, so a missing index can't silently break
            // search, and documents are findable by title even before OCR completes.
            String pattern = escapeRegex(criteria.term().trim());
            and.add(
                    new Criteria()
                            .orOperator(
                                    Criteria.where("title").regex(pattern, "i"),
                                    Criteria.where("eap_number").regex(pattern, "i"),
                                    Criteria.where("description").regex(pattern, "i"),
                                    Criteria.where("ocr_text").regex(pattern, "i")));
        }
        // Optional facet filters.
        addEquals(and, "document_type", criteria.documentType());
        addEquals(and, "organizational_unit", criteria.department());
        addEquals(and, "folder_id", criteria.folderId());
        addEquals(and, "ocr_status", criteria.ocrStatus());
        if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            Criteria date = Criteria.where("upload_date");
            if (criteria.dateFrom() != null) {
                date = date.gte(criteria.dateFrom());
            }
            if (criteria.dateTo() != null) {
                date = date.lte(criteria.dateTo());
            }
            and.add(date);
        }

        // A single top-level $and avoids key collisions between the permission $or
        // and the term $or.
        Query query = new Query(new Criteria().andOperator(and.toArray(new Criteria[0])));

        long total = mongoTemplate.count(query, DocumentRecord.class);

        query.with(sortFor(criteria.sort())).skip((long) page * limit).limit(limit);
        List<SearchResultEntry> content =
                mongoTemplate.find(query, DocumentRecord.class).stream()
                        .map(record -> SearchResultEntry.from(record, criteria.term()))
                        .toList();

        int totalPages = (int) Math.ceil((double) total / limit);
        boolean hasMore = (long) (page + 1) * limit < total;
        return new SearchResponse(content, page, limit, total, totalPages, hasMore);
    }

    private static boolean hasAnyFilter(SearchCriteria c) {
        return notBlank(c.documentType())
                || notBlank(c.department())
                || notBlank(c.folderId())
                || notBlank(c.ocrStatus())
                || c.dateFrom() != null
                || c.dateTo() != null;
    }

    private static void addEquals(List<Criteria> and, String field, String value) {
        if (notBlank(value)) {
            and.add(Criteria.where(field).is(value.trim()));
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Escapes regex metacharacters so a user's term is matched literally. */
    private static String escapeRegex(String term) {
        return term.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    private static Sort sortFor(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "upload_date");
        }
        return switch (sort.toLowerCase()) {
            case "date_asc" -> Sort.by(Sort.Direction.ASC, "upload_date");
            case "title_asc" -> Sort.by(Sort.Direction.ASC, "title");
            case "title_desc" -> Sort.by(Sort.Direction.DESC, "title");
            default -> Sort.by(Sort.Direction.DESC, "upload_date");
        };
    }

    /**
     * Exact-name lookup for CMIS {@code WHERE cmis:name = '...'} queries. Matches
     * active documents whose title equals {@code name} (case-insensitive) and
     * applies the same per-user RBAC predicate as {@link #search}. Unlike
     * full-text search this does not require the document to be indexed, since a
     * name lookup is metadata-only.
     */
    public List<SearchResultEntry> searchByName(String name, CurrentUser user, int page, int limit) {
        if (name == null || name.isBlank() || user == null) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safePage = Math.max(page, 0);

        Query query = new Query();
        query.addCriteria(
                Criteria.where("title").regex("^" + Pattern.quote(name.trim()) + "$", "i"));
        query.addCriteria(Criteria.where("document_status").is(DocumentService.STATUS_ACTIVE));
        Criteria permission = permissionFilter(user);
        if (permission != null) {
            query.addCriteria(permission);
        }

        query.with(Sort.by(Sort.Direction.DESC, "upload_date"))
                .skip((long) safePage * safeLimit)
                .limit(safeLimit);

        return mongoTemplate.find(query, DocumentRecord.class).stream()
                .map(SearchResultEntry::from)
                .toList();
    }

    /**
     * Per-user permission predicate. Admins bypass it entirely (they may see all
     * documents); every other caller is restricted to documents whose ACL grants
     * them via role, department, or ownership. Returning {@code null} means "no
     * restriction" (admin).
     */
    private Criteria permissionFilter(CurrentUser user) {
        if (user.roles() != null && user.roles().contains(PermissionService.ROLE_ADMIN)) {
            return null;
        }

        List<Criteria> clauses = new ArrayList<>();
        if (user.roles() != null && !user.roles().isEmpty()) {
            clauses.add(Criteria.where("acl.allowed_roles").in(user.roles()));
        }
        if (user.department() != null && !user.department().isBlank()) {
            clauses.add(Criteria.where("acl.allowed_departments").in(List.of(user.department())));
        }
        if (user.username() != null && !user.username().isBlank()) {
            clauses.add(Criteria.where("acl.owner").is(user.username()));
        }

        if (clauses.isEmpty()) {
            // No identity to match on → match nothing rather than everything.
            return Criteria.where("_id").is(null);
        }
        return new Criteria().orOperator(clauses.toArray(new Criteria[0]));
    }
}
