package de.hof.dms.service;

import de.hof.dms.domain.EapSequence;
import de.hof.dms.exception.ApiException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.regex.Pattern;

/**
 * Allocates and validates EAP numbers of the form
 * {@code CATEGORY-DEPARTMENT-YEAR-SEQUENCE}. Sequence values are handed out
 * atomically per (category, department, year) via an upserting
 * find-and-modify counter, so concurrent uploads never receive duplicate numbers.
 */
@Service
@Profile("!no-mongo")
public class EapNumberService {

    public static final Pattern EAP_FORMAT =
            Pattern.compile("^[0-9]{1,10}-[A-Z]{2,10}-[0-9]{4}-[0-9]{6}$");

    private static final Pattern CATEGORY_FORMAT = Pattern.compile("^[0-9]{1,10}$");
    private static final Pattern DEPARTMENT_FORMAT = Pattern.compile("^[A-Z]{2,10}$");

    private final MongoTemplate mongoTemplate;

    public EapNumberService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Allocates the next EAP number for the given category and department in the
     * current year, atomically incrementing the per-group sequence counter.
     *
     * @param department department code; blank values default to {@code GEN}
     * @return a newly allocated, format-valid EAP number
     * @throws ApiException if the category/department are invalid or the sequence
     *     could not be allocated
     */
    public String generateNext(String category, String department) {
        validateCategory(category);
        String dept = normalizeDepartment(department);

        int year = Year.now().getValue();
        Query query =
                Query.query(
                        Criteria.where("category")
                                .is(category)
                                .and("department")
                                .is(dept)
                                .and("year")
                                .is(year));
        Update update =
                new Update()
                        .inc("seq", 1)
                        .setOnInsert("category", category)
                        .setOnInsert("department", dept)
                        .setOnInsert("year", year);
        FindAndModifyOptions options =
                FindAndModifyOptions.options().returnNew(true).upsert(true);

        EapSequence counter =
                mongoTemplate.findAndModify(query, update, options, EapSequence.class, "eap_sequences");
        if (counter == null || counter.getSeq() <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to allocate EAP sequence");
        }

        String eapNumber = category + "-" + dept + "-" + year + "-" + String.format("%06d", counter.getSeq());
        validateFormat(eapNumber);
        return eapNumber;
    }

    /**
     * Validates that a value matches the full EAP number format.
     *
     * @throws ApiException with 400 if the format is invalid
     */
    public void validateFormat(String eapNumber) {
        if (eapNumber == null || !EAP_FORMAT.matcher(eapNumber).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid EAP number format; expected CATEGORY-DEPARTMENT-YEAR-SEQUENCE");
        }
    }

    /**
     * Validates that the EAP category is 1-10 digits.
     *
     * @throws ApiException with 400 if the category is invalid
     */
    public void validateCategory(String category) {
        if (category == null || !CATEGORY_FORMAT.matcher(category).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "eapCategory must be 1-10 digits (e.g. 1234)");
        }
    }

    private String normalizeDepartment(String department) {
        if (department == null || department.isBlank()) {
            return "GEN";
        }
        String normalized = department.trim().toUpperCase();
        if (!DEPARTMENT_FORMAT.matcher(normalized).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "department must be 2-10 uppercase letters (e.g. ITDLZ)");
        }
        return normalized;
    }
}
