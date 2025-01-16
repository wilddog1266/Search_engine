package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class LemmaFinderService {
    private final LuceneMorphology luceneMorphology;
    private static final String[] particleNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    public LemmaFinderService(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    public static LemmaFinderService getInstance() throws IOException {
        LuceneMorphology morphology = new RussianLuceneMorphology();
        return new LemmaFinderService(morphology);
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for(String word : words) {
            if(word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if(anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if(normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if(lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }

    public boolean isStopWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }

        List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);

        return anyWordBaseBelongToParticle(wordBaseForms);
    }

    public Set<String> getLemmaSet(String text) {
        String[] textArray = arrayContainsRussianWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordForm(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    public String removeHtmlTags(String text) {
        if(text == null) {
            return null;
        }
        return text.replaceAll("<[^>]*>","");
    }

    public String getSnippet(String content, String query) {
        String lowerCaseContent = content.toLowerCase();
        String lowerCaseQuery = query.toLowerCase();

        int index = lowerCaseContent.indexOf(lowerCaseQuery);
        if(index == -1) {
            return "";
        }
        int start = Math.max(0, index - 30);
        int end = Math.min(content.length(), index + query.length() + 30);

        String snippet = content.substring(start, end).replace(query, "<b>" + query + "</b>");
        return "..." + snippet + "...";
    }



    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    private boolean hasParticleProperty(String wordBase) {
        for(String property : particleNames) {
            if(wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean isCorrectWordForm(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        }
        return true;
    }
}
