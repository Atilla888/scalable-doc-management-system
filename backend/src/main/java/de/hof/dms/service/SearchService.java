package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.SearchResultEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@Profile("!no-mongo")
public class SearchService {

    private static final int MAX_RESULTS = 50;

    private final MongoTemplate mongoTemplate;

    public SearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<SearchResultEntry> search(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String escaped = Pattern.quote(term.trim());
        Criteria match =
                new Criteria()
                        .orOperator(
                                Criteria.where("title").regex(escaped, "i"),
                                Criteria.where("description").regex(escaped, "i"),
                                Criteria.where("eap_number").regex(escaped, "i"),
                                Criteria.where("ocr_text").regex(escaped, "i"));

        Query query =
                new Query()
                        .addCriteria(
                                Criteria.where("document_status")
                                        .is(DocumentService.STATUS_ACTIVE)
                                        .andOperator(match))
                        .with(Sort.by(Sort.Direction.DESC, "upload_date"))
                        .limit(MAX_RESULTS);

        return mongoTemplate.find(query, DocumentRecord.class).stream()
                .map(SearchResultEntry::from)
                .toList();
    }
}
