package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.SearchResultEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-text document search with RBAC enforced at query time.
 *
 * <p>Every query combines, in a single MongoDB request (never post-filtered):
 * the {@code $text} match against the documents text index (title, description,
 * ocr_text), an active + indexed status gate, and a mandatory per-user
 * permission predicate built from the caller's token. Results are mapped to
 * {@link SearchResultEntry}, so ACL fields and raw OCR text never reach the client.
 */
@Service
@Profile("!no-mongo")
public class SearchService {

    static final String INDEXED = "indexed";
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final MongoTemplate mongoTemplate;

    public SearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<SearchResultEntry> search(String term, CurrentUser user, int page, int limit) {
        if (term == null || term.isBlank() || user == null) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        int safePage = Math.max(page, 0);

        Query query = new Query();
        // Full-text match against the documents text index (title, description, ocr_text).
        query.addCriteria(TextCriteria.forDefaultLanguage().matching(term.trim()));
        // Only active, fully-indexed documents are searchable.
        query.addCriteria(Criteria.where("document_status").is(DocumentService.STATUS_ACTIVE));
        query.addCriteria(Criteria.where("indexing_status").is(INDEXED));
        // Mandatory RBAC predicate, applied in the same query — never post-filtered.
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
