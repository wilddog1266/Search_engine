package searchengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.util.Lazy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "site", indexes = {
        @Index(name = "idx_site_url", columnList = "url")
})
@Getter
@Setter
public class SiteModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SiteStatusEnum status;

    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private LocalDateTime statusTime; // Если INDEXING -> дата и время должны обновляться по мере добавления сайтов

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String mainUrl;

    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String siteName;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PageModel> pages = new ArrayList<>();

    @Override
    public String toString() {
        return "SiteModel{" +
                "id=" + id +
                ", status=" + status +
                ", statusTime=" + statusTime +
                ", lastError='" + lastError + '\'' +
                ", mainUrl='" + mainUrl + '\'' +
                ", siteName='" + siteName + '\'' +
                '}';
    }
}
