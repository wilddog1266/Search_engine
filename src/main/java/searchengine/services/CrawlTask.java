package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrawlTask extends RecursiveAction {
    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);
    private final String url;
    private final SiteModel siteModel;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final String userAgent;
    private final String referrer;
    private final AtomicBoolean stopIndexing;
    private final LemmaFinderService lemmaFinderService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public CrawlTask(String url,
                     SiteModel siteModel,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     String userAgent,
                     String referrer,
                     AtomicBoolean stopIndexing, LemmaFinderService lemmaFinderService, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.url = url;
        this.siteModel = siteModel;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.stopIndexing = stopIndexing;
        this.lemmaFinderService = lemmaFinderService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    public void compute() {
        if (stopIndexing.get()) {
            logger.info("Индексация остановлена для URL: {}", url);
            updateSiteStatus(siteModel, SiteStatusEnum.FAILED, "Индексация остановлена пользователем");
            return;
        }

        delay();

        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer);

            Connection.Response response = connection.execute();

            String contentType = response.contentType();
            logger.info("Тип контента для URL {}: {}", url, contentType);
            if (contentType == null || !(contentType.startsWith("text/")
                    || contentType.endsWith("xml")
                    || contentType.endsWith("+xml")
                    || contentType.endsWith("zip")
                    || contentType.endsWith("sql"))) {
                logger.warn("Неподдерживаемый тип контента: {} для URL: {}", contentType, url);
                return;
            }

            Document document = response.parse();
            int statusCode = response.statusCode();
            String content = document.html();

            PageModel pageModel = new PageModel();
            pageModel.setSite(siteModel);
            pageModel.setPath(url);
            pageModel.setCode(statusCode);
            pageModel.setContent(content);

            savePageModel(pageModel);
            logger.info("Сохранение страницы: {}", pageModel);

            // Извлечение лемм
            Map<String, Integer> lemmas = lemmaFinderService.collectLemmas(content);
            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                int frequency = entry.getValue();

                List<LemmaModel> lemmaModels = lemmaRepository.findAllByLemma(lemmaText);
                if (lemmaModels.isEmpty()) {
                    LemmaModel lemmaModel = new LemmaModel();
                    lemmaModel.setLemma(lemmaText);
                    lemmaModel.setFrequency(frequency);
                    lemmaModel.setSiteId(siteModel);
                    lemmaRepository.save(lemmaModel);

                    IndexModel indexModel = new IndexModel();
                    indexModel.setPageId(pageModel);
                    indexModel.setLemmaId(lemmaModel);
                    indexModel.setRank(frequency);
                    indexRepository.save(indexModel);
                } else {
                    for(LemmaModel lemmaModel : lemmaModels) {
                        lemmaModel.setFrequency(lemmaModel.getFrequency() + frequency);
                        lemmaRepository.save(lemmaModel);

                        IndexModel indexModel = new IndexModel();
                        indexModel.setPageId(pageModel);
                        indexModel.setLemmaId(lemmaModel);
                        indexModel.setRank(frequency);
                        indexRepository.save(indexModel);
                    }
                }
            }

            // Обновление статуса сайта
            siteModel.setStatusTime(LocalDateTime.now());
            logger.info("Обновление статуса сайта: {}", siteModel);
            updateSiteStatus(siteModel, SiteStatusEnum.INDEXING, null);

            // Обработка ссылок
            if (stopIndexing.get()) {
                logger.info("Индексация остановлена перед обработкой ссылок для URL: {}", url);
                siteModel.setLastError("Индексация остановлена пользователем");
                siteModel.setStatusTime(LocalDateTime.now());
                siteModel.setStatus(SiteStatusEnum.FAILED);
                return;
            }

            Elements links = document.select("a[href]");
            List<CrawlTask> subTasks = new ArrayList<>();
            for (Element link : links) {
                String linkUrl = link.attr("abs:href");
                if (linkUrl.startsWith(siteModel.getMainUrl()) && !pageRepository.existsByPath(linkUrl)) {
                    subTasks.add(new CrawlTask(linkUrl,
                            siteModel,
                            pageRepository,
                            siteRepository,
                            userAgent,
                            referrer,
                            stopIndexing,
                            lemmaFinderService, lemmaRepository, indexRepository));
                    logger.info("Добавлен новый Task для URL: {}", linkUrl);
                }
            }
            invokeAll(subTasks);
        } catch (Exception e) {
            logger.error("Ошибка прохождения URL: {}", url, e);
            updateSiteStatus(siteModel, SiteStatusEnum.FAILED, e.getMessage());
        }
    }

    @Transactional
    private void updateSiteStatus(SiteModel siteModel, SiteStatusEnum status, String errorMessage) {
        siteModel.setStatus(status);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setLastError(errorMessage);
        siteRepository.saveAndFlush(siteModel);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void savePageModel(PageModel pageModel) {
        pageRepository.saveAndFlush(pageModel);
    }
    private void delay() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
