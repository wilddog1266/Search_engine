package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexModel, Long> {
    List<IndexModel> findByPageId(PageModel page);
    List<IndexModel> findByLemmaId(LemmaModel lemma);
}