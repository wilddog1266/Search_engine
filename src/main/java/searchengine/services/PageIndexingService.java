package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.List;

@Service
public class PageIndexingService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageService pageService;

    public ResponseEntity<IndexingResponse> indexPage(String path, List<Site> sitesList) {
        IndexingResponse response = new IndexingResponse();

        for (Site siteConfig : sitesList) {
            String siteUrl = siteConfig.getUrl();

            if (path.startsWith(siteUrl)) {
                SiteModel siteModel = siteRepository.findByMainUrl(siteUrl);
                if (siteModel == null) {
                    response.setResult(false);
                    response.setError("Сайт не найден в базе данных");
                    return ResponseEntity.badRequest().body(response);
                }

                PageModel existingPage = pageRepository.findByPath(path);
                if (existingPage != null) {
                    try {
                        pageService.indexPage(path, siteUrl);
                        response.setResult(true);
                        return ResponseEntity.ok(response);
                    } catch (IOException e) {
                        response.setResult(false);
                        response.setError("Ошибка при индексации страницы: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                } else {
                    try {
                        pageService.indexPage(path, siteUrl);
                        response.setResult(true);
                        return ResponseEntity.ok(response);
                    } catch (IOException e) {
                        response.setResult(false);
                        response.setError("Ошибка при индексации страницы: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                }
            }
        }

        response.setResult(false);
        response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        return ResponseEntity.badRequest().body(response);
    }
}
