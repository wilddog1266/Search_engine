package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class PageService {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaFinderService lemmaFinderService;
    @Autowired
    public PageService(PageRepository pageRepository,
                       LemmaRepository lemmaRepository,
                       IndexRepository indexRepository,
                       SiteRepository siteRepository,
                       LemmaFinderService lemmaFinderService) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        this.lemmaFinderService = lemmaFinderService;
    }

    @Transactional
    public void indexPage(String path, String siteUrl) throws IOException {
        SiteModel siteModel = siteRepository.findByMainUrl(siteUrl);
        if(siteModel == null) {
            throw new IllegalArgumentException("Сайт с URL: " + siteUrl + " не найден в базе данных");
        }

        PageModel existingPage = pageRepository.findByPath(path);
        if(existingPage != null) {
            deletePageData(existingPage);
        }

        Document doc;
        try {
            doc = Jsoup.connect(siteUrl + path).get();
        } catch (IOException e) {
            throw new IOException("Ошибка при получении страницы: " + e.getMessage());
        }
        String html = doc.html();
        int responseCode = doc.connection().response().statusCode();

        PageModel page = new PageModel();
        page.setPath(path);
        page.setSite(siteModel);
        page.setCode(responseCode);
        page.setContent(html);
        pageRepository.save(page);

        Map<String, Integer> lemmaFrequency = lemmaFinderService.collectLemmas(lemmaFinderService.removeHtmlTags(html));
        for(Map.Entry<String, Integer> entry : lemmaFrequency.entrySet()) {
            String lemmaText = entry.getKey();
            Integer frequency = entry.getValue();

            LemmaModel lemma = lemmaRepository.findByLemma(lemmaText);
            if(lemma == null) {
                lemma = new LemmaModel();
                lemma.setLemma(lemmaText);
                lemma.setFrequency(1);
                lemma.setSiteId(siteModel);
                lemmaRepository.save(lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);
            }

            IndexModel index = new IndexModel();
            index.setPageId(page);
            index.setLemmaId(lemma);
            index.setRank(frequency);
            indexRepository.save(index);
        }
    }
    @Transactional
    public void deletePageData(PageModel pageModel) {
        List<IndexModel> indexModelList = indexRepository.findByPageId(pageModel);
        indexRepository.deleteAll(indexModelList);

        pageRepository.delete(pageModel);

        for(IndexModel index : indexModelList) {
            LemmaModel lemma = index.getLemmaId();
            List<IndexModel> relatedIndexes = indexRepository.findByLemmaId(lemma);
            if(relatedIndexes.isEmpty()) {
                lemmaRepository.delete(lemma);
            }
        }
    }
    public double calculateRelevance(PageModel page, String query) {
        String content = lemmaFinderService.removeHtmlTags(page.getContent());
        String lowerCaseContent = content.toLowerCase();
        String lowerCaseQuery = query.toLowerCase();

        int occurrences = lowerCaseContent.split(lowerCaseQuery, -1).length - 1;

        double relevance = (double) occurrences / content.length();
        return relevance;
    }

    public String getPageTitle(String content) {
        Document doc = Jsoup.parse(content);
        Element titleElement = doc.selectFirst("title");
        if (titleElement != null) {
            return titleElement.text();
        }

        Element h1Element = doc.selectFirst("h1");
        return (h1Element != null) ? h1Element.text() : "Заголовка нет";
    }


}