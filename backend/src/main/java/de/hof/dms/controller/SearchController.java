package de.hof.dms.controller;

import de.hof.dms.dto.SearchCriteria;
import de.hof.dms.dto.SearchResponse;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.SearchService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/search")
@Profile("!no-mongo")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "folder", required = false) String folder,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        // Accept the spec's `query` param; fall back to `q` for the existing frontend.
        String term = (query != null && !query.isBlank()) ? query : q;
        CurrentUser user = CurrentUser.fromJwt(jwt);
        SearchCriteria criteria =
                new SearchCriteria(
                        term,
                        type,
                        department,
                        folder,
                        status,
                        parseDayStart(dateFrom),
                        parseDayEnd(dateTo),
                        sort,
                        page,
                        limit);
        return searchService.query(criteria, user);
    }

    /** Parses a {@code yyyy-MM-dd} date to the start of that day (UTC); null/invalid → null. */
    private static Instant parseDayStart(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Parses a {@code yyyy-MM-dd} date to the end of that day (UTC), inclusive; null/invalid → null. */
    private static Instant parseDayEnd(String date) {
        LocalDate parsed = parseDate(date);
        return parsed == null ? null : parsed.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
