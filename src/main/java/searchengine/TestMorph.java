package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.controllers.ApiController;
import searchengine.model.PageModel;
import searchengine.services.LemmaFinderService;
import searchengine.services.PageService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TestMorph {
    public static void main(String[] args) throws IOException {
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        LemmaFinderService lemmaFinderService = new LemmaFinderService(luceneMorphology);
        String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";
        Map<String, Integer> map = lemmaFinderService.collectLemmas(text);
        Set<String> set = lemmaFinderService.getLemmaSet(text);
        for(Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println("Слово: " + entry.getKey() + " кол-во: " + entry.getValue());
        }
        PageModel pageModel = new PageModel();

    }
}
