package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.SiteStatusEnum;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.LemmaFinderService;
import searchengine.services.PageService;
import searchengine.services.SiteIndexingService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteIndexingService siteIndexingService;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;
    private final LemmaFinderService lemmaFinderService;
    private final PageService pageService;
    private final PageRepository pageRepository;

    @GetMapping("/statistic")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        boolean allSitesIndexed = true;
        siteIndexingService.resetStopIndexing();

        for (Site siteConfig : sitesList.getSites()) {
            String siteUrl = siteConfig.getUrl();
            SiteModel siteModel = siteRepository.findByMainUrl(siteUrl);

            if (siteModel != null && siteModel.getStatus() == SiteStatusEnum.INDEXING) {
                response.put("result", false);
                response.put("error", "Индексация уже запущена для сайта: " + siteUrl);
                return ResponseEntity.ok(response);
            }

            try {
                siteIndexingService.indexSite(siteUrl);
            } catch (Exception e) {
                allSitesIndexed = false;
                response.put("error", e.getMessage());
                break;
            }
        }
        response.put("result", allSitesIndexed);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> response = new HashMap<>();

        try {
            if(!siteIndexingService.isIndexing()) {
                response.put("result", false);
                response.put("error", "Индексация не запущена");
                return ResponseEntity.ok(response);
            }
            if(siteIndexingService.isIndexing()) {
                List<SiteModel> siteModels = siteRepository.findAll();
                for(SiteModel site : siteModels) {
                    siteIndexingService.updateSiteStatus(site, SiteStatusEnum.FAILED, "Индексация остановлена пользователем");
                }
            }

            Thread.sleep(3000);

            siteIndexingService.stopIndexing();

            response.put("result", true);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка при остановке индексации: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    //TODO: Ошибка в проверке правильности введенной ссылки ИСПРАВИТЬ!!!
    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestBody String path) {
        Map<String, Object> response = new HashMap<>();
        boolean urlCorrect = false;

        for (Site siteConfig : sitesList.getSites()) {
            String siteUrl = siteConfig.getUrl();

            if (path.startsWith(siteUrl) || path.equals(siteUrl)) {
                urlCorrect = true;

                SiteModel siteModel = siteRepository.findByMainUrl(siteUrl);
                if (siteModel == null) {
                    response.put("result", false);
                    response.put("error", "Сайт не найден в базе данных");
                    return ResponseEntity.badRequest().body(response);
                }

                PageModel existingPage = pageRepository.findByPath(path);
                if (existingPage != null) {
                    try {
                        pageService.indexPage(path, siteUrl);
                        response.put("result", true);
                        return ResponseEntity.ok(response);
                    } catch (IOException e) {
                        response.put("result", false);
                        response.put("error", "Ошибка при индексации страницы: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                } else {
                    try {
                        pageService.indexPage(path, siteUrl);
                        response.put("result", true);
                        return ResponseEntity.ok(response);
                    } catch (IOException e) {
                        response.put("result", false);
                        response.put("error", "Ошибка при индексации страницы: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                }
            }
        }

        response.put("result", false);
        response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        return ResponseEntity.badRequest().body(response);
    }


    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistic() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> statistic = new HashMap<>();
        List<Map<String, Object>> detailedStatistic = new ArrayList<>();

        List<SiteModel> sites = siteRepository.findAll();

        int totalSites = sites.size();
        int totalPages = 0;
        int totalLemmas = 0;
        boolean indexingInProgress = false;

        for(SiteModel siteModel : sites) {
            List<PageModel> pageModelList = siteModel.getPages();
            Map<String, Integer> lemmas = new HashMap<>();
            int totalLemmasForSite = 0;

            for(PageModel page : pageModelList) {
                String pageText = lemmaFinderService.removeHtmlTags(page.getContent());
                Map<String, Integer> pageLemmas = lemmaFinderService.collectLemmas(pageText);
                totalLemmasForSite += pageLemmas.values().stream().mapToInt(Integer::intValue).sum();
            }
            totalPages += pageRepository.findAll().size();
            totalLemmas += totalLemmasForSite;

            Map<String, Object> siteStats = new HashMap<>();
            siteStats.put("url", siteModel.getMainUrl());
            siteStats.put("name", siteModel.getSiteName());
            siteStats.put("status", siteModel.getStatus().name());
            siteStats.put("statusTime", siteModel.getStatusTime());

            if(siteModel.getStatus() == SiteStatusEnum.INDEXING) {
                indexingInProgress = true;
            }

            if(siteModel.getStatus() == SiteStatusEnum.FAILED) {
                siteStats.put("error", "Ошибка индексации: " + siteModel.getLastError());
            }

            siteStats.put("pages", siteModel.getPages().size());
            siteStats.put("lemmas", totalLemmasForSite);
            detailedStatistic.add(siteStats);
        }
        statistic.put("total", Map.of(
                "sites", totalSites,
                "pages", totalPages,
                "lemmas", totalLemmas,
                "indexing", indexingInProgress
        ));

        statistic.put("detailed", detailedStatistic);

        response.put("result", true);
        response.put("statistics", statistic);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    private ResponseEntity<Map<String, Object>> search(@RequestParam String query,
                                                       @RequestParam(value = "site", required = false) String siteURL,
                                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                       @RequestParam(value = "limit", defaultValue = "20") int limit) {

        Map<String, Object> response = new HashMap<>();

        log.info("Начало выполнения поискового запроса: query={}, site={}, offset={}, limit={}", query, siteURL, offset,  limit);

        if (query == null || query.isEmpty()) {
            response.put("result", false);
            response.put("error", "Введите поисковой запрос");
            return ResponseEntity.badRequest().body(response);
        }

        List<SiteModel> sitesList = siteRepository.findAll();
        if (sitesList.isEmpty()) {
            response.put("result", false);
            response.put("error", "Нет проиндексированных сайтов");
            return ResponseEntity.badRequest().body(response);
        }

        List<PageModel> pages = pageRepository.findAll();
        Set<Map<String, Object>> results = new HashSet<>();
        int totalCount = 0;

        for (PageModel page : pages) {
            String content = lemmaFinderService.removeHtmlTags(page.getContent());
            Map<String, Integer> lemmas = lemmaFinderService.collectLemmas(content);

            if (lemmas.keySet().stream().anyMatch(lemma -> lemma.contains(query))) {
                totalCount++;
                Map<String, Object> result = new HashMap<>();
                result.put("site", page.getSite().getMainUrl());
                result.put("siteName", page.getSite().getSiteName());
                result.put("url", page.getPath());
                result.put("title", pageService.getPageTitle(content));
                result.put("snippet", lemmaFinderService.getSnippet(content, query));
                result.put("relevance", pageService.calculateRelevance(page, query));
                results.add(result);
            }
        }


        List<Map<String, Object>> resultList = new ArrayList<>(results);


        int start = Math.min(offset, resultList.size());
        int end = Math.min(start + limit, resultList.size());
        List<Map<String, Object>> paginatedResults = resultList.subList(start, end);

        response.put("result", true);
        response.put("count", totalCount);
        response.put("data", paginatedResults);

        log.info("Поиск завершен. Найдено результатов: {}, отображено: {}", totalCount, paginatedResults.size());

        return ResponseEntity.ok(response);
    }

}