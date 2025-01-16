//package searchengine.services;
//
//import org.jsoup.Connection;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import searchengine.config.Site;
//import searchengine.config.SitesList;
//import searchengine.model.PageModel;
//import searchengine.model.SiteModel;
//import searchengine.model.SiteStatusEnum;
//import searchengine.repositories.PageRepository;
//import searchengine.repositories.SiteRepository;
//
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.HashSet;
//import java.util.Optional;
//import java.util.Set;
//
//@Service
//public class SiteIndexingService {
//    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);
//    private final SiteRepository siteRepository;
//    private final PageRepository pageRepository;
//    private final String userAgent;
//    private final String referrer;
//    private final SitesList sitesList;
//    private volatile boolean stopIndexing = false;
//
//    public SiteIndexingService(SiteRepository siteRepository, PageRepository pageRepository,
//                               @Value("${user-agent}") String userAgent, @Value("${referrer}") String referrer, SitesList sitesList) {
//        this.siteRepository = siteRepository;
//        this.pageRepository = pageRepository;
//        this.userAgent = userAgent;
//        this.referrer = referrer;
//        this.sitesList = sitesList;
//    }
//
//    @Transactional
//    public void indexSite(String siteUrl) {
//        logger.info("Начало индексации сайта: {}", siteUrl);
//        SiteModel siteModel = null;
//        try {
//            deleteExistingData(siteUrl);
//            siteModel = createSiteRecord(siteUrl);
//            crawlSite(siteModel);
//            if (!stopIndexing) {
//                updateSiteStatus(siteModel, SiteStatusEnum.INDEXED, null);
//            }
//        } catch (Exception e) {
//            logger.error("Ошибка индексации сайта: {}", siteUrl, e);
//            if (siteModel != null) {
//                updateSiteStatus(siteModel, SiteStatusEnum.FAILED, e.getMessage());
//            }
//        }
//    }
//
//    @Transactional
//    public void deleteExistingData(String site) {
//        Optional<SiteModel> existingSiteOpt = Optional.ofNullable(siteRepository.findByMainUrl(site));
//        existingSiteOpt.ifPresent(siteRepository::delete);
//    }
//
//    @Transactional
//    private SiteModel createSiteRecord(String site) {
//        Site siteConfig = sitesList.getSites().stream()
//                .filter(s -> s.getUrl().equalsIgnoreCase(site))
//                .findFirst()
//                .orElse(null);
//        if (siteConfig == null) {
//            throw new IllegalArgumentException("Сайта не найдено в конфигурации");
//        }
//        SiteModel siteModel = new SiteModel();
//        siteModel.setMainUrl(site);
//        siteModel.setSiteName(siteConfig.getName());
//        siteModel.setStatus(SiteStatusEnum.INDEXING);
//        siteModel.setStatusTime(LocalDateTime.now());
//        return siteRepository.save(siteModel);
//    }
//
//    @Transactional
//    private void updateSiteStatus(SiteModel siteModel, SiteStatusEnum status, String errorMessage) {
//        siteModel.setStatus(status);
//        siteModel.setStatusTime(LocalDateTime.now());
//        siteModel.setLastError(errorMessage);
//        siteRepository.save(siteModel);
//    }
//
//    @Transactional
//    private void crawlSite(SiteModel siteModel) {
//        Set<String> visitedUrls = new HashSet<>();
//        String baseUrl = siteModel.getMainUrl();
//        crawlPage(siteModel, baseUrl, visitedUrls);
//    }
//
//    @Transactional
//    private void crawlPage(SiteModel siteModel, String url, Set<String> visitedUrls) {
//        if (stopIndexing || visitedUrls.contains(url)) {
//            return;
//        }
//
//        visitedUrls.add(url);
//
//        try {
//            Connection connection = Jsoup.connect(url)
//                    .userAgent(userAgent)
//                    .referrer(referrer);
//
//            Connection.Response response = connection.execute();
//
//            // Проверка типа контента
//            String contentType = response.contentType();
//            logger.info("Тип контента для URL {}: {}", url, contentType);
//            if (contentType == null || !(contentType.startsWith("text/")
//                    || contentType.endsWith("xml")
//                    || contentType.endsWith("+xml")
//                    || contentType.endsWith("zip")
//                    || contentType.endsWith("sql"))) {
//                logger.warn("Неподдерживаемый тип контента: {} для URL: {}", contentType, url);
//                return;
//            }
//
//            Document document = response.parse();
//            int statusCode = response.statusCode();
//            String content = document.html();
//
//            if (!pageRepository.existsByPath(url)) {
//                PageModel pageModel = new PageModel();
//                pageModel.setSite(siteModel);
//                pageModel.setPath(url.replace(siteModel.getMainUrl(), ""));
//                pageModel.setCode(statusCode);
//                pageModel.setContent(content);
//                pageRepository.save(pageModel);
//            }
//
//            siteModel.setStatusTime(LocalDateTime.now());
//            siteRepository.save(siteModel);
//
//            Elements links = document.select("a[href]");
//            for (Element link : links) {
//                String linkUrl = link.absUrl("href");
//                if (linkUrl.startsWith(siteModel.getMainUrl()) && !visitedUrls.contains(linkUrl)) {
//                    crawlPage(siteModel, linkUrl, visitedUrls);
//                }
//            }
//        } catch (IOException e) {
//            logger.error("Ошибка при обходе страницы: {}", url, e);
//            updateSiteStatus(siteModel, SiteStatusEnum.FAILED, e.getMessage());
//        }
//    }
//}
// Ниже многопоточный класс
package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.SiteModel;
import searchengine.model.SiteStatusEnum;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SiteIndexingService {
    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final AtomicBoolean stopIndexing = new AtomicBoolean(false);
    private final LemmaFinderService lemmaFinderService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public SiteIndexingService(SiteRepository siteRepository, PageRepository pageRepository,
                               SitesList sitesList, LemmaFinderService lemmaFinderService,
                               LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
        this.lemmaFinderService = lemmaFinderService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Async
    public void startIndexing() {
        resetStopIndexing();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Site siteConfig : sitesList.getSites()) {
            String siteUrl = siteConfig.getUrl();
            SiteModel siteModel = siteRepository.findByMainUrl(siteUrl);

            if (siteModel != null && siteModel.getStatus() == SiteStatusEnum.INDEXING) {
                logger.warn("Индексация уже запущена для сайта: {}", siteUrl);
                continue;
            }

            futures.add(CompletableFuture.runAsync(() -> indexSite(siteUrl)));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Transactional
    public void indexSite(String siteUrl) {
        logger.info("Начало индексации сайта: {}", siteUrl);
        SiteModel siteModel = null;
        try {
            deleteExistingData(siteUrl);
            siteModel = createSiteRecord(siteUrl);

            ForkJoinPool forkJoinPool = new ForkJoinPool();
            forkJoinPool.invoke(new CrawlTask(siteUrl, siteModel, pageRepository, siteRepository,
                    "userAgent", "referrer", stopIndexing, lemmaFinderService, lemmaRepository, indexRepository));

            if (!stopIndexing.get()) {
                updateSiteStatus(siteModel, SiteStatusEnum.INDEXED, null);
            }
        } catch (Exception e) {
            logger.error("Ошибка индексации сайта: {}", siteUrl, e);
            if (siteModel != null) {
                updateSiteStatus(siteModel, SiteStatusEnum.FAILED, e.getMessage());
            }
        }
    }

    public void stopIndexing() {
        stopIndexing.set(true);
        logger.info("Индексация остановлена");
    }

    @Transactional
    public void deleteExistingData(String site) {
        Optional<SiteModel> existingSiteOpt = Optional.ofNullable(siteRepository.findByMainUrl(site));
        existingSiteOpt.ifPresent(siteRepository::delete);
    }

    private SiteModel createSiteRecord(String site) {
        Site siteConfig = sitesList.getSites().stream()
                .filter(s -> s.getUrl().equalsIgnoreCase(site))
                .findFirst()
                .orElse(null);
        if (siteConfig == null) {
            throw new IllegalArgumentException("Сайта не найдено в конфигурации");
        }
        SiteModel siteModel = new SiteModel();
        siteModel.setMainUrl(site);
        siteModel.setSiteName(siteConfig.getName());
        siteModel.setStatus(SiteStatusEnum.INDEXING);
        siteModel.setStatusTime(LocalDateTime.now());
        return siteRepository.save(siteModel);
    }

    @Transactional
    public void updateSiteStatus(SiteModel siteModel, SiteStatusEnum status, String errorMessage) {
        siteModel.setStatus(status);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setLastError(errorMessage);
        siteRepository.save(siteModel);
    }

    public boolean isIndexing() {
        List<SiteModel> sites = siteRepository.findAll();
        return sites.stream().anyMatch(site -> site.getStatus() == SiteStatusEnum.INDEXING);
    }

    public void resetStopIndexing() {
        stopIndexing.set(false);
    }
}