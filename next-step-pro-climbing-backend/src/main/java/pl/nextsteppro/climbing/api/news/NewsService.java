package pl.nextsteppro.climbing.api.news;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.news.NewsDtos.*;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.news.NewsContentBlockRepository;
import pl.nextsteppro.climbing.domain.news.NewsRepository;
import pl.nextsteppro.climbing.domain.news.NewsSummaryProjection;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NewsService {

    static final int DEFAULT_PAGE_SIZE = 12;

    private final NewsRepository newsRepository;
    private final NewsContentBlockRepository blockRepository;
    private final String baseUrl;

    public NewsService(NewsRepository newsRepository,
                       NewsContentBlockRepository blockRepository,
                       @Value("${app.base-url}") String baseUrl) {
        this.newsRepository = newsRepository;
        this.blockRepository = blockRepository;
        this.baseUrl = baseUrl;
    }

    @Cacheable(value = "newsList", key = "#page + '-' + #size")
    public NewsPageDto getAllPublished(int page, int size) {
        Page<NewsSummaryProjection> result = newsRepository.findAllPublishedSummaries(
                PageRequest.of(page, size));

        List<NewsSummaryDto> content = result.getContent()
                .stream()
                .map(this::toSummaryDto)
                .toList();

        return new NewsPageDto(content, page, size, result.getTotalElements(), result.hasNext());
    }

    @Cacheable(value = "newsDetail", key = "#id")
    public NewsDetailDto getPublishedById(UUID id) {
        var news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News not found"));

        if (!news.isPublished()) {
            throw new IllegalArgumentException("News not found");
        }

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);

        return new NewsDetailDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                news.getThumbnailFilename() != null ? buildFileUrl(news.getThumbnailFilename()) : null,
                blocks.stream().map(this::toBlockDto).toList(),
                news.getPublishedAt()
        );
    }

    private NewsSummaryDto toSummaryDto(NewsSummaryProjection projection) {
        return new NewsSummaryDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                projection.getThumbnailFilename() != null ? buildFileUrl(projection.getThumbnailFilename()) : null,
                projection.getPublishedAt()
        );
    }

    private ContentBlockDto toBlockDto(NewsContentBlock block) {
        return new ContentBlockDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/news/" + filename;
    }
}
