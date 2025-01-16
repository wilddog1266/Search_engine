package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResponse;
import searchengine.dto.statistics.SearchResult;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaFinderService lemmaFinderService;

    @Autowired
    private PageService pageService;

    public ResponseEntity<SearchResponse> search(String query, String siteURL, int offset, int limit) {
        SearchResponse response = new SearchResponse();

        if (query == null || query.isEmpty()) {
            response.setResult(false);
            response.setError("Введите поисковой запрос");
            return ResponseEntity.badRequest().body(response);
        }

        List<SiteModel> sitesList = siteRepository.findAll();
        if (sitesList.isEmpty()) {
            response.setResult(false);
            response.setError("Нет проиндексированных сайтов");
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, Integer> lemmasMap = lemmaFinderService.collectLemmas(query);
        List<String> lemmas = new ArrayList<>(lemmasMap.keySet());

        lemmas.removeIf(lemma -> lemmaFinderService.isStopWord(lemma));

        List<PageModel> pages = pageRepository.findAll();

        List<PageModel> filteredPages = new ArrayList<>();
        for (PageModel page : pages) {
            String content = lemmaFinderService.removeHtmlTags(page.getContent());
            Map<String, Integer> pageLemmas = lemmaFinderService.collectLemmas(content);

            boolean containsAllLemmas = lemmas.stream()
                    .allMatch(pageLemmas::containsKey);

            if (containsAllLemmas) {
                filteredPages.add(page);
            }
        }

        List<SearchResult> results = new ArrayList<>();
        double maxRelevance = 0.0;

        for (PageModel page : filteredPages) {
            String content = lemmaFinderService.removeHtmlTags(page.getContent());
            Map<String, Integer> pageLemmas = lemmaFinderService.collectLemmas(content);

            double absoluteRelevance = lemmas.stream()
                    .mapToDouble(lemma -> pageLemmas.getOrDefault(lemma, 0))
                    .sum();

            if (absoluteRelevance > maxRelevance) {
                maxRelevance = absoluteRelevance;
            }

            SearchResult result = new SearchResult();
            result.setSite(page.getSite().getMainUrl());
            result.setSiteName(page.getSite().getSiteName());
            result.setUrl(page.getPath());
            result.setTitle(pageService.getPageTitle(content));
            result.setSnippet(lemmaFinderService.getSnippet(content, query));
            result.setRelevance(maxRelevance > 0 ? absoluteRelevance / maxRelevance : 0);
            results.add(result);
        }

        results.sort((r1, r2) -> Double.compare(r2.getRelevance(), r1.getRelevance()));

        int start = Math.min(offset, results.size());
        int end = Math.min(start + limit, results.size());
        List<SearchResult> paginatedResults = results.subList(start, end);

        response.setResult(true);
        response.setCount(results.size());
        response.setData(paginatedResults);

        return ResponseEntity.ok(response);
    }
}
