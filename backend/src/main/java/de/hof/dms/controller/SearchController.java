package de.hof.dms.controller;

import de.hof.dms.dto.SearchResultEntry;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.SearchService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Profile("!no-mongo")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchResultEntry> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        // Accept the spec's `query` param; fall back to `q` for the existing frontend.
        String term = (query != null && !query.isBlank()) ? query : q;
        CurrentUser user = CurrentUser.fromJwt(jwt);
        return searchService.search(term, user, page, limit);
    }
}
