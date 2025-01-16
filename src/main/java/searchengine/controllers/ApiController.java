package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.statistics.*;
import searchengine.services.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {


    private final SiteIndexingService siteIndexingService;
    private final SitesList sitesList;
    private final PageIndexingService pageIndexingService;
    private final StatisticsServiceImpl statisticsService;
    private final SearchService searchService;

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing(){
        IndexingResponse response = new IndexingResponse();

        if (siteIndexingService.isIndexing()) {
            response.setResult(false);
            response.setError("Индексация уже запущена");
            return ResponseEntity.ok(response);
        }

        siteIndexingService.startIndexing();

        response.setResult(true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        IndexingResponse response = new IndexingResponse();

        if (!siteIndexingService.isIndexing()) {
            response.setResult(false);
            response.setError("Индексация не запущена");
            return ResponseEntity.ok(response);
        }

        siteIndexingService.stopIndexing();
        response.setResult(true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestBody String path) {
        return pageIndexingService.indexPage(path, sitesList.getSites());
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> getStatistic() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(value = "site", required = false) String siteURL,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return searchService.search(query, siteURL, offset, limit);
    }
}