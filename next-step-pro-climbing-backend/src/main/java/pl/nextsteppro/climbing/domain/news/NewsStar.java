package pl.nextsteppro.climbing.domain.news;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_news_stars")
public class NewsStar {

    @EmbeddedId
    private NewsStarId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NewsStar() {}

    public NewsStar(NewsStarId id) {
        this.id = id;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public NewsStarId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
