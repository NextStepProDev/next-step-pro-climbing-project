package pl.nextsteppro.climbing.api.news;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.news.NewsDtos.*;
import pl.nextsteppro.climbing.domain.news.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NewsService {

    static final int DEFAULT_PAGE_SIZE = 12;

    private final NewsRepository newsRepository;
    private final NewsContentBlockRepository blockRepository;
    private final NewsStarRepository newsStarRepository;
    private final String baseUrl;

    public NewsService(NewsRepository newsRepository,
                       NewsContentBlockRepository blockRepository,
                       NewsStarRepository newsStarRepository,
                       @Value("${app.base-url}") String baseUrl) {
        this.newsRepository = newsRepository;
        this.blockRepository = blockRepository;
        this.newsStarRepository = newsStarRepository;
        this.baseUrl = baseUrl;
    }

    @Cacheable(value = "newsList", key = "#page + '-' + #size",
               condition = "#q == null && !#starred && #userId == null")
    public NewsPageDto getAllPublished(int page, int size,
                                      @Nullable String q,
                                      boolean starred,
                                      @Nullable UUID userId) {
        PageRequest pageable = PageRequest.of(page, size);

        if (starred && userId == null) {
            return new NewsPageDto(List.of(), page, size, 0, false);
        }

        Set<UUID> starredIds = starred ? newsStarRepository.findNewsIdsByIdUserId(userId) : Set.of();

        if (starred && starredIds.isEmpty()) {
            return new NewsPageDto(List.of(), page, size, 0, false);
        }

        Page<NewsSummaryProjection> result;
        if (starred && q != null && !q.isBlank()) {
            result = newsRepository.findAllPublishedSummariesByTitleAndIds(q, starredIds, pageable);
        } else if (starred) {
            result = newsRepository.findAllPublishedSummariesByIds(starredIds, pageable);
        } else if (q != null && !q.isBlank()) {
            result = newsRepository.findAllPublishedSummariesByTitle(q, pageable);
        } else {
            result = newsRepository.findAllPublishedSummaries(pageable);
        }

        Set<UUID> userStarred = userId != null
                ? (starred ? starredIds : newsStarRepository.findNewsIdsByIdUserId(userId))
                : Set.of();

        List<NewsSummaryDto> content = result.getContent()
                .stream()
                .map(p -> toSummaryDto(p, userId != null ? userStarred.contains(p.getId()) : null))
                .toList();

        return new NewsPageDto(content, page, size, result.getTotalElements(), result.hasNext());
    }

    @Cacheable(value = "newsDetail", key = "#id", condition = "#userId == null")
    public NewsDetailDto getPublishedById(UUID id, @Nullable UUID userId) {
        var news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News not found"));

        if (!news.isPublished()) {
            throw new IllegalArgumentException("News not found");
        }

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);

        Boolean starred = userId != null
                ? newsStarRepository.existsByIdUserIdAndIdNewsId(userId, id)
                : null;

        return new NewsDetailDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                buildThumbnailUrl(news.getThumbnailUrl(), news.getThumbnailFilename()),
                news.getThumbnailFocalPointX(),
                news.getThumbnailFocalPointY(),
                blocks.stream().map(this::toBlockDto).toList(),
                news.getPublishedAt(),
                starred
        );
    }

    @Transactional
    public void starNews(UUID newsId, UUID userId) {
        newsRepository.findById(newsId)
                .filter(News::isPublished)
                .orElseThrow(() -> new IllegalArgumentException("News not found"));

        NewsStarId starId = new NewsStarId(userId, newsId);
        if (!newsStarRepository.existsById(starId)) {
            newsStarRepository.save(new NewsStar(starId));
        }
    }

    @Transactional
    public void unstarNews(UUID newsId, UUID userId) {
        newsStarRepository.deleteByIdUserIdAndIdNewsId(userId, newsId);
    }

    private NewsSummaryDto toSummaryDto(NewsSummaryProjection projection, @Nullable Boolean starred) {
        return new NewsSummaryDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                buildThumbnailUrl(projection.getThumbnailUrl(), projection.getThumbnailFilename()),
                projection.getThumbnailFocalPointX(),
                projection.getThumbnailFocalPointY(),
                projection.getPublishedAt(),
                starred
        );
    }

    private ContentBlockDto toBlockDto(NewsContentBlock block) {
        String imageUrl = block.getImageUrl() != null
                ? block.getImageUrl()
                : (block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null);
        return new ContentBlockDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                imageUrl,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private String buildThumbnailUrl(@Nullable String thumbnailUrl, @Nullable String thumbnailFilename) {
        if (thumbnailUrl != null) return thumbnailUrl;
        if (thumbnailFilename != null) return buildFileUrl(thumbnailFilename);
        return null;
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/news/" + filename;
    }
}
