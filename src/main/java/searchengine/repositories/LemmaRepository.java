package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaModel;

import java.util.List;

public interface LemmaRepository extends JpaRepository<LemmaModel, Long> {
    LemmaModel findByLemma(String lemma);

    List<LemmaModel> findAllByLemma(String lemma);

}
