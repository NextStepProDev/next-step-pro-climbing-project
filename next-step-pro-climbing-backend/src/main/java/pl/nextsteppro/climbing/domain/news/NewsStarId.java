package pl.nextsteppro.climbing.domain.news;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class NewsStarId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "news_id", nullable = false)
    private UUID newsId;

    protected NewsStarId() {}

    public NewsStarId(UUID userId, UUID newsId) {
        this.userId = userId;
        this.newsId = newsId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getNewsId() {
        return newsId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsStarId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(newsId, other.newsId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, newsId);
    }
}
