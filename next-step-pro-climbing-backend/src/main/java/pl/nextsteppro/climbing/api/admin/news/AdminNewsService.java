package pl.nextsteppro.climbing.api.admin.news;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.news.AdminNewsDtos.*;
import pl.nextsteppro.climbing.domain.news.*;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminNewsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminNewsService.class);

    private final NewsRepository newsRepository;
    private final NewsContentBlockRepository blockRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminNewsService(NewsRepository newsRepository,
                            NewsContentBlockRepository blockRepository,
                            FileStorageService fileStorageService,
                            @Value("${app.base-url}") String baseUrl) {
        this.newsRepository = newsRepository;
        this.blockRepository = blockRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    // --- Artykuły ---

    @Transactional(readOnly = true)
    public AdminNewsPageDto getAllNews(int page, int size) {
        Page<NewsSummaryProjection> result = newsRepository.findAllSummaries(PageRequest.of(page, size));
        List<NewsAdminDto> content = result.getContent().stream().map(this::toAdminDto).toList();
        return new AdminNewsPageDto(content, page, size, result.getTotalElements(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public NewsDetailAdminDto getNews(UUID id) {
        News news = findNews(id);
        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);

        return toDetailAdminDto(news, blocks);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsAdminDto createNews(CreateNewsRequest request) {
        News news = new News(request.title());
        news.setExcerpt(request.excerpt());
        news = newsRepository.save(news);
        return toAdminDto(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsAdminDto updateNewsMeta(UUID id, UpdateNewsMetaRequest request) {
        News news = findNews(id);

        if (request.title() != null) {
            news.setTitle(request.title());
        }
        if (request.excerpt() != null) {
            news.setExcerpt(request.excerpt());
        }

        news = newsRepository.save(news);
        return toAdminDto(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsAdminDto setPublished(UUID id, boolean publish) {
        News news = findNews(id);

        if (publish && news.getPublishedAt() == null) {
            news.setPublishedAt(Instant.now());
        }
        news.setPublished(publish);

        news = newsRepository.save(news);
        return toAdminDto(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void deleteNews(UUID id) {
        News news = findNews(id);

        // Usuń pliki bloków IMAGE
        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);
        for (NewsContentBlock block : blocks) {
            if (block.getBlockType() == BlockType.IMAGE && block.getImageFilename() != null) {
                try {
                    fileStorageService.delete(block.getImageFilename(), "news");
                } catch (IOException e) {
                    logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
                }
            }
        }

        // Usuń miniaturkę
        if (news.getThumbnailFilename() != null) {
            try {
                fileStorageService.delete(news.getThumbnailFilename(), "news");
            } catch (IOException e) {
                logger.warn("Failed to delete thumbnail file: {} - {}", news.getThumbnailFilename(), e.getMessage());
            }
        }

        newsRepository.delete(news);
    }

    // --- Miniaturka ---

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsDetailAdminDto uploadThumbnail(UUID id, MultipartFile file) throws IOException {
        News news = findNews(id);

        if (news.getThumbnailFilename() != null) {
            try {
                fileStorageService.delete(news.getThumbnailFilename(), "news");
            } catch (IOException e) {
                logger.warn("Failed to delete old thumbnail: {} - {}", news.getThumbnailFilename(), e.getMessage());
            }
        }

        String filename = fileStorageService.store(file, "news");
        news.setThumbnailFilename(filename);
        newsRepository.save(news);

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);
        return toDetailAdminDto(news, blocks);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void deleteThumbnail(UUID id) throws IOException {
        News news = findNews(id);

        if (news.getThumbnailFilename() == null) {
            throw new IllegalStateException("No thumbnail to delete");
        }

        fileStorageService.delete(news.getThumbnailFilename(), "news");
        news.setThumbnailFilename(null);
        newsRepository.save(news);
    }

    // --- Bloki treści ---

    @CacheEvict(value = "newsDetail", allEntries = true)
    public ContentBlockAdminDto addTextBlock(UUID newsId, AddTextBlockRequest request) {
        News news = findNews(newsId);
        int order = blockRepository.findMaxDisplayOrder(newsId) + 1;

        NewsContentBlock block = new NewsContentBlock(news, BlockType.TEXT);
        block.setContent(request.content());
        block.setDisplayOrder(order);

        block = blockRepository.save(block);
        return toBlockAdminDto(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public UploadBlockImageResponse addImageBlock(UUID newsId, MultipartFile file, @Nullable String caption) throws IOException {
        News news = findNews(newsId);
        int order = blockRepository.findMaxDisplayOrder(newsId) + 1;

        String filename = fileStorageService.store(file, "news");

        NewsContentBlock block = new NewsContentBlock(news, BlockType.IMAGE);
        block.setImageFilename(filename);
        block.setCaption(caption);
        block.setDisplayOrder(order);

        block = blockRepository.save(block);

        return new UploadBlockImageResponse(
                block.getId(),
                filename,
                buildFileUrl(filename),
                block.getDisplayOrder()
        );
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void updateTextBlock(UUID blockId, UpdateTextBlockRequest request) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() != BlockType.TEXT) {
            throw new IllegalArgumentException("Block is not a TEXT block");
        }

        block.setContent(request.content());
        blockRepository.save(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void updateImageBlock(UUID blockId, UpdateImageBlockRequest request) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() != BlockType.IMAGE) {
            throw new IllegalArgumentException("Block is not an IMAGE block");
        }

        block.setCaption(request.caption());
        blockRepository.save(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void deleteBlock(UUID blockId) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() == BlockType.IMAGE && block.getImageFilename() != null) {
            try {
                fileStorageService.delete(block.getImageFilename(), "news");
            } catch (IOException e) {
                logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
            }
        }

        blockRepository.delete(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void moveBlock(UUID blockId, String direction) {
        NewsContentBlock block = findBlock(blockId);
        UUID newsId = block.getNews().getId();

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId);

        int position = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId().equals(blockId)) {
                position = i;
                break;
            }
        }

        if (position < 0) {
            return;
        }

        if ("UP".equals(direction) && position > 0) {
            NewsContentBlock other = blocks.get(position - 1);
            int tmpOrder = block.getDisplayOrder();
            block.setDisplayOrder(other.getDisplayOrder());
            other.setDisplayOrder(tmpOrder);
            blockRepository.save(block);
            blockRepository.save(other);
        } else if ("DOWN".equals(direction) && position < blocks.size() - 1) {
            NewsContentBlock other = blocks.get(position + 1);
            int tmpOrder = block.getDisplayOrder();
            block.setDisplayOrder(other.getDisplayOrder());
            other.setDisplayOrder(tmpOrder);
            blockRepository.save(block);
            blockRepository.save(other);
        }
    }

    // --- Helpery ---

    private News findNews(UUID id) {
        return newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News not found"));
    }

    private NewsContentBlock findBlock(UUID id) {
        return blockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
    }

    private NewsAdminDto toAdminDto(NewsSummaryProjection projection) {
        return new NewsAdminDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                projection.getThumbnailFilename() != null ? buildFileUrl(projection.getThumbnailFilename()) : null,
                projection.isPublished(),
                projection.getPublishedAt(),
                projection.getCreatedAt(),
                projection.getUpdatedAt()
        );
    }

    private NewsAdminDto toAdminDto(News news) {
        return new NewsAdminDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                news.getThumbnailFilename() != null ? buildFileUrl(news.getThumbnailFilename()) : null,
                news.isPublished(),
                news.getPublishedAt(),
                news.getCreatedAt(),
                news.getUpdatedAt()
        );
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void updateThumbnailFocalPoint(UUID id, AdminNewsDtos.UpdateThumbnailFocalPointRequest req) {
        News news = findNews(id);
        news.setThumbnailFocalPointX(req.focalPointX());
        news.setThumbnailFocalPointY(req.focalPointY());
        newsRepository.save(news);
    }

    private NewsDetailAdminDto toDetailAdminDto(News news, List<NewsContentBlock> blocks) {
        return new NewsDetailAdminDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                news.getThumbnailFilename(),
                news.getThumbnailFilename() != null ? buildFileUrl(news.getThumbnailFilename()) : null,
                news.getThumbnailFocalPointX(),
                news.getThumbnailFocalPointY(),
                news.isPublished(),
                news.getPublishedAt(),
                blocks.stream().map(this::toBlockAdminDto).toList(),
                news.getCreatedAt(),
                news.getUpdatedAt()
        );
    }

    private ContentBlockAdminDto toBlockAdminDto(NewsContentBlock block) {
        return new ContentBlockAdminDto(
                block.getId(),
                block.getBlockType().name(),
                block.getContent(),
                block.getImageFilename(),
                block.getImageFilename() != null ? buildFileUrl(block.getImageFilename()) : null,
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private String buildFileUrl(String filename) {
        return baseUrl + "/api/files/news/" + filename;
    }
}
