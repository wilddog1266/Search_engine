package searchengine.dto.statistics;

import lombok.Data;

@Data
public class SearchResult {
    private String site;
    private String siteName;
    private String url;
    private String title;
    private String snippet;
    private double relevance;
}
