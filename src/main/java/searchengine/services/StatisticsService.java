package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {
    ResponseEntity<StatisticsResponse> getStatistics();
}
