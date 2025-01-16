package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.SiteStatusEnum;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaFinderService lemmaFinderService;

    public ResponseEntity<StatisticsResponse> getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData statistics = new StatisticsData();
        List<DetailedStatisticsItem> detailedStatistic = new ArrayList<>();

        List<SiteModel> sites = siteRepository.findAll();

        int totalSites = sites.size();
        int totalPages = 0;
        int totalLemmas = 0;
        boolean indexingInProgress = false;

        for (SiteModel siteModel : sites) {
            List<PageModel> pageModelList = siteModel.getPages();
            int totalLemmasForSite = 0;

            for (PageModel page : pageModelList) {
                String pageText = lemmaFinderService.removeHtmlTags(page.getContent());
                Map<String, Integer> pageLemmas = lemmaFinderService.collectLemmas(pageText);
                totalLemmasForSite += pageLemmas.values().stream().mapToInt(Integer::intValue).sum();
            }
            totalPages += pageRepository.findAll().size();
            totalLemmas += totalLemmasForSite;

            DetailedStatisticsItem siteStats = new DetailedStatisticsItem();
            siteStats.setUrl(siteModel.getMainUrl());
            siteStats.setName(siteModel.getSiteName());
            siteStats.setStatus(siteModel.getStatus().name());
            siteStats.setStatusTime(siteModel.getStatusTime().toEpochSecond(ZoneOffset.UTC));

            if (siteModel.getStatus() == SiteStatusEnum.INDEXING) {
                indexingInProgress = true;
            }

            if (siteModel.getStatus() == SiteStatusEnum.FAILED) {
                siteStats.setError("Ошибка индексации: " + siteModel.getLastError());
            }

            siteStats.setPages(siteModel.getPages().size());
            siteStats.setLemmas(totalLemmasForSite);
            detailedStatistic.add(siteStats);
        }

        TotalStatistics totalStats = new TotalStatistics();
        totalStats.setSites(totalSites);
        totalStats.setPages(totalPages);
        totalStats.setLemmas(totalLemmas);
        totalStats.setIndexing(indexingInProgress);

        statistics.setTotal(totalStats);
        statistics.setDetailed(detailedStatistic);

        response.setResult(true);
        response.setStatistics(statistics);
        return ResponseEntity.ok(response);
    }
}