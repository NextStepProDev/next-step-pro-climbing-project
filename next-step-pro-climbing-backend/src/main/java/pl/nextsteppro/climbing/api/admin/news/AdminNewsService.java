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
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.NewsletterMailService;
import pl.nextsteppro.climbing.infrastructure.media.VideoEmbedUrls;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileUrls;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AdminNewsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminNewsService.class);

    private final NewsRepository newsRepository;
    private final NewsContentBlockRepository blockRepository;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final NewsletterMailService newsletterMailService;
    private final String baseUrl;

    public AdminNewsService(NewsRepository newsRepository,
                            NewsContentBlockRepository blockRepository,
                            FileStorageService fileStorageService,
                            UserRepository userRepository,
                            NewsletterMailService newsletterMailService,
                            @Value("${app.base-url}") String baseUrl) {
        this.newsRepository = newsRepository;
        this.blockRepository = blockRepository;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.newsletterMailService = newsletterMailService;
        this.baseUrl = baseUrl;
    }

    // --- Articles ---

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
        news.setLanguage(request.language() != null ? request.language() : "pl");
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

        if (publish) {
            news.setPublishedAt(Instant.now());
        }
        news.setPublished(publish);
        news = newsRepository.save(news);

        for (News sibling : newsRepository.findByTranslationGroupId(news.getTranslationGroupId())) {
            if (!sibling.getId().equals(news.getId()) && sibling.isPublished() != publish) {
                if (publish) {
                    sibling.setPublishedAt(Instant.now());
                }
                sibling.setPublished(publish);
                newsRepository.save(sibling);
            }
        }

        return toAdminDto(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsAdminDto updatePublishedAt(UUID id, Instant publishedAt) {
        News news = findNews(id);
        news.setPublishedAt(publishedAt);
        news = newsRepository.save(news);

        for (News sibling : newsRepository.findByTranslationGroupId(news.getTranslationGroupId())) {
            if (!sibling.getId().equals(news.getId())) {
                sibling.setPublishedAt(publishedAt);
                newsRepository.save(sibling);
            }
        }

        return toAdminDto(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void deleteNews(UUID id) {
        News news = findNews(id);

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);
        for (NewsContentBlock block : blocks) {
            if (block.getBlockType() == BlockType.IMAGE && block.getImageFilename() != null) {
                if (!blockRepository.existsByImageFilenameAndIdNot(block.getImageFilename(), block.getId())) {
                    try {
                        fileStorageService.delete(block.getImageFilename(), "news");
                    } catch (Exception e) {
                        logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
                    }
                }
            }
        }

        if (news.getThumbnailFilename() != null) {
            if (!newsRepository.existsByThumbnailFilenameAndIdNot(news.getThumbnailFilename(), id)) {
                try {
                    fileStorageService.delete(news.getThumbnailFilename(), "news");
                } catch (Exception e) {
                    logger.warn("Failed to delete thumbnail file: {} - {}", news.getThumbnailFilename(), e.getMessage());
                }
            }
        }

        blockRepository.deleteAll(blocks);
        newsRepository.delete(news);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsDetailAdminDto duplicateAsTranslation(UUID id, String targetLanguage) {
        News source = findNews(id);

        if (source.getLanguage().equals(targetLanguage)) {
            throw new LocalizedMessage.Rejected("error.translation.same.language");
        }

        if (newsRepository.existsByTranslationGroupIdAndLanguage(source.getTranslationGroupId(), targetLanguage)) {
            throw new LocalizedMessage.Rejected("error.translation.exists");
        }

        News copy = new News(source.getTitle());
        copy.setExcerpt(source.getExcerpt());
        copy.setThumbnailFilename(source.getThumbnailFilename());
        copy.setThumbnailUrl(source.getThumbnailUrl());
        copy.setThumbnailFocalPointX(source.getThumbnailFocalPointX());
        copy.setThumbnailFocalPointY(source.getThumbnailFocalPointY());
        copy.setLanguage(targetLanguage);
        copy.setTranslationGroupId(source.getTranslationGroupId());
        copy.setPublished(source.isPublished());
        if (source.getPublishedAt() != null) {
            copy.setPublishedAt(source.getPublishedAt());
        }
        copy = newsRepository.save(copy);

        List<NewsContentBlock> sourceBlocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);
        for (NewsContentBlock srcBlock : sourceBlocks) {
            NewsContentBlock copyBlock = new NewsContentBlock(copy, srcBlock.getBlockType());
            copyBlock.setContent(srcBlock.getContent());
            copyBlock.setImageFilename(srcBlock.getImageFilename());
            copyBlock.setImageUrl(srcBlock.getImageUrl());
            copyBlock.setCaption(srcBlock.getCaption());
            copyBlock.setDisplayOrder(srcBlock.getDisplayOrder());
            blockRepository.save(copyBlock);
        }

        List<NewsContentBlock> copyBlocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(copy.getId());
        return toDetailAdminDto(copy, copyBlocks);
    }

    // --- Thumbnail (with sync to siblings in the translation group) ---

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public NewsDetailAdminDto uploadThumbnail(UUID id, MultipartFile file) throws IOException {
        News news = findNews(id);
        String oldFilename = news.getThumbnailFilename();

        String filename = fileStorageService.store(file, "news");
        news.setThumbnailFilename(filename);
        news.setThumbnailUrl(null);
        newsRepository.save(news);

        syncThumbnailToSiblings(news);

        if (oldFilename != null && !newsRepository.existsByThumbnailFilenameAndIdNot(oldFilename, id)) {
            try {
                fileStorageService.delete(oldFilename, "news");
            } catch (Exception e) {
                logger.warn("Failed to delete old thumbnail: {} - {}", oldFilename, e.getMessage());
            }
        }

        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(id);
        return toDetailAdminDto(news, blocks);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void deleteThumbnail(UUID id) throws IOException {
        News news = findNews(id);

        if (news.getThumbnailFilename() == null && news.getThumbnailUrl() == null) {
            throw new LocalizedMessage.Conflict("error.thumbnail.none");
        }

        String oldFilename = news.getThumbnailFilename();

        news.setThumbnailFilename(null);
        news.setThumbnailUrl(null);
        news.setThumbnailFocalPointX(null);
        news.setThumbnailFocalPointY(null);
        newsRepository.save(news);

        syncThumbnailToSiblings(news);

        if (oldFilename != null && !newsRepository.existsByThumbnailFilenameAndIdNot(oldFilename, id)) {
            try {
                fileStorageService.delete(oldFilename, "news");
            } catch (Exception e) {
                logger.warn("Failed to delete thumbnail: {} - {}", oldFilename, e.getMessage());
            }
        }
    }

    // --- Content blocks ---

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
                FileUrls.of(baseUrl, "news", filename),
                block.getDisplayOrder()
        );
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public ContentBlockAdminDto addImageBlockFromUrl(UUID newsId, AddImageBlockFromUrlRequest request) {
        News news = findNews(newsId);
        int order = blockRepository.findMaxDisplayOrder(newsId) + 1;

        NewsContentBlock block = new NewsContentBlock(news, BlockType.IMAGE);
        block.setImageUrl(request.imageUrl());
        block.setCaption(request.caption());
        block.setDisplayOrder(order);

        block = blockRepository.save(block);
        return toBlockAdminDto(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public ContentBlockAdminDto addVideoEmbedBlock(UUID newsId, AdminNewsDtos.AddVideoEmbedBlockRequest request) {
        News news = findNews(newsId);
        int order = blockRepository.findMaxDisplayOrder(newsId) + 1;

        String embedUrl = normalizeVideoEmbedUrl(request.url());

        NewsContentBlock block = new NewsContentBlock(news, BlockType.VIDEO_EMBED);
        block.setContent(embedUrl);
        block.setDisplayOrder(order);

        block = blockRepository.save(block);
        return toBlockAdminDto(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void updateVideoEmbedBlock(UUID blockId, AdminNewsDtos.UpdateVideoEmbedBlockRequest request) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() != BlockType.VIDEO_EMBED) {
            throw new LocalizedMessage.Rejected("error.block.wrong.type");
        }

        block.setContent(normalizeVideoEmbedUrl(request.url()));
        blockRepository.save(block);
    }

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public void setThumbnailUrl(UUID id, SetThumbnailUrlRequest request) {
        News news = findNews(id);
        String oldFilename = news.getThumbnailFilename();

        news.setThumbnailFilename(null);
        news.setThumbnailUrl(request.thumbnailUrl());
        newsRepository.save(news);

        syncThumbnailToSiblings(news);

        if (oldFilename != null && !newsRepository.existsByThumbnailFilenameAndIdNot(oldFilename, id)) {
            try {
                fileStorageService.delete(oldFilename, "news");
            } catch (Exception e) {
                logger.warn("Failed to delete old thumbnail when setting URL: {}", e.getMessage());
            }
        }
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void updateTextBlock(UUID blockId, UpdateTextBlockRequest request) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() != BlockType.TEXT) {
            throw new LocalizedMessage.Rejected("error.block.wrong.type");
        }

        block.setContent(request.content());
        blockRepository.save(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void updateImageBlock(UUID blockId, UpdateImageBlockRequest request) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() != BlockType.IMAGE) {
            throw new LocalizedMessage.Rejected("error.block.wrong.type");
        }

        block.setCaption(request.caption());
        blockRepository.save(block);
    }

    @CacheEvict(value = "newsDetail", allEntries = true)
    public void deleteBlock(UUID blockId) {
        NewsContentBlock block = findBlock(blockId);

        if (block.getBlockType() == BlockType.IMAGE && block.getImageFilename() != null) {
            if (!blockRepository.existsByImageFilenameAndIdNot(block.getImageFilename(), block.getId())) {
                try {
                    fileStorageService.delete(block.getImageFilename(), "news");
                } catch (Exception e) {
                    logger.warn("Failed to delete block image file: {} - {}", block.getImageFilename(), e.getMessage());
                }
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

    // --- Media sync to translations ---

    @CacheEvict(value = {"newsList", "newsDetail"}, allEntries = true)
    public SyncMediaResultDto syncMediaToTranslations(UUID sourceNewsId) {
        News source = findNews(sourceNewsId);
        List<NewsContentBlock> sourceBlocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(sourceNewsId);

        List<NewsContentBlock> sourceMediaBlocks = sourceBlocks.stream()
                .filter(b -> b.getBlockType() == BlockType.IMAGE || b.getBlockType() == BlockType.VIDEO_EMBED)
                .toList();

        if (sourceMediaBlocks.isEmpty()) {
            return new SyncMediaResultDto(0);
        }

        List<News> siblings = newsRepository.findByTranslationGroupId(source.getTranslationGroupId())
                .stream()
                .filter(s -> !s.getId().equals(sourceNewsId))
                .toList();

        int totalAdded = 0;

        for (News sibling : siblings) {
            List<NewsContentBlock> siblingBlocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(sibling.getId());
            int maxOrder = siblingBlocks.stream()
                    .mapToInt(NewsContentBlock::getDisplayOrder)
                    .max()
                    .orElse(-1);

            for (NewsContentBlock srcBlock : sourceMediaBlocks) {
                boolean alreadyExists = siblingBlocks.stream().anyMatch(sb -> mediaBlockMatches(sb, srcBlock));
                if (!alreadyExists) {
                    maxOrder++;
                    NewsContentBlock copy = new NewsContentBlock(sibling, srcBlock.getBlockType());
                    copy.setContent(srcBlock.getContent());
                    copy.setImageFilename(srcBlock.getImageFilename());
                    copy.setImageUrl(srcBlock.getImageUrl());
                    copy.setCaption(srcBlock.getCaption());
                    copy.setDisplayOrder(maxOrder);
                    blockRepository.save(copy);
                    totalAdded++;
                }
            }
        }

        return new SyncMediaResultDto(totalAdded);
    }

    private boolean mediaBlockMatches(NewsContentBlock existing, NewsContentBlock source) {
        if (existing.getBlockType() != source.getBlockType()) {
            return false;
        }
        if (source.getBlockType() == BlockType.IMAGE) {
            if (source.getImageFilename() != null) {
                return source.getImageFilename().equals(existing.getImageFilename());
            }
            if (source.getImageUrl() != null) {
                return source.getImageUrl().equals(existing.getImageUrl());
            }
            return false;
        }
        if (source.getBlockType() == BlockType.VIDEO_EMBED) {
            return source.getContent() != null && source.getContent().equals(existing.getContent());
        }
        return false;
    }

    // --- Newsletter ---

    @Transactional(readOnly = true)
    public NewsletterSentDto sendNewsNewsletter(UUID id) {
        News news = findNews(id);
        if (!news.isPublished()) {
            throw new LocalizedMessage.Conflict("error.news.newsletter.unpublished");
        }
        // Confirmed addresses only, as in AdminService#sendMailToUsers: the tick can come from the
        // registration form, so an unconfirmed account (a typo, somebody else's address) would get
        // marketing it never agreed to for the seven days before it is deleted.
        List<User> subscribers = userRepository.findAllByNewsletterSubscribedTrue().stream()
            .filter(User::isEmailVerified)
            .toList();

        // Each reader gets the article in their own language when that translation is published;
        // the version the admin sent stands in where there is none. It used to go out in one
        // language to everybody, framed in the reader's language around somebody else's text.
        Map<String, News> byLanguage = new HashMap<>();
        for (News version : newsRepository.findByTranslationGroupId(news.getTranslationGroupId())) {
            if (version.isPublished()) byLanguage.put(version.getLanguage(), version);
        }
        Map<News, List<User>> recipients = new LinkedHashMap<>();
        recipients.put(news, new ArrayList<>());
        for (User subscriber : subscribers) {
            News version = byLanguage.getOrDefault(subscriber.getPreferredLanguage(), news);
            recipients.computeIfAbsent(version, v -> new ArrayList<>()).add(subscriber);
        }
        recipients.forEach((version, readers) -> {
            if (readers.isEmpty()) return;
            List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(version.getId());
            newsletterMailService.sendToAll(version, blocks, List.copyOf(readers), baseUrl);
        });
        return new NewsletterSentDto(subscribers.size());
    }

    // --- Helpers ---

    private void syncThumbnailToSiblings(News source) {
        for (News sibling : newsRepository.findByTranslationGroupId(source.getTranslationGroupId())) {
            if (!sibling.getId().equals(source.getId())) {
                String oldFilename = sibling.getThumbnailFilename();

                sibling.setThumbnailFilename(source.getThumbnailFilename());
                sibling.setThumbnailUrl(source.getThumbnailUrl());
                sibling.setThumbnailFocalPointX(source.getThumbnailFocalPointX());
                sibling.setThumbnailFocalPointY(source.getThumbnailFocalPointY());
                newsRepository.save(sibling);

                if (oldFilename != null
                        && !oldFilename.equals(source.getThumbnailFilename())
                        && !newsRepository.existsByThumbnailFilenameAndIdNot(oldFilename, sibling.getId())) {
                    try {
                        fileStorageService.delete(oldFilename, "news");
                    } catch (Exception e) {
                        logger.warn("Failed to delete old sibling thumbnail: {} - {}", oldFilename, e.getMessage());
                    }
                }
            }
        }
    }

    private News findNews(UUID id) {
        return newsRepository.findById(id)
                .orElseThrow(() -> new LocalizedMessage.Rejected("error.not.found.news"));
    }

    private NewsContentBlock findBlock(UUID id) {
        return blockRepository.findById(id)
                .orElseThrow(() -> new LocalizedMessage.Rejected("error.not.found.block"));
    }

    private NewsAdminDto toAdminDto(NewsSummaryProjection projection) {
        return new NewsAdminDto(
                projection.getId(),
                projection.getTitle(),
                projection.getExcerpt(),
                imageUrl(projection.getThumbnailUrl(), projection.getThumbnailFilename()),
                projection.isPublished(),
                projection.getPublishedAt(),
                projection.getLanguage(),
                projection.getTranslationGroupId(),
                projection.getCreatedAt(),
                projection.getUpdatedAt()
        );
    }

    private NewsAdminDto toAdminDto(News news) {
        return new NewsAdminDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                imageUrl(news.getThumbnailUrl(), news.getThumbnailFilename()),
                news.isPublished(),
                news.getPublishedAt(),
                news.getLanguage(),
                news.getTranslationGroupId(),
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

        syncThumbnailToSiblings(news);
    }

    private NewsDetailAdminDto toDetailAdminDto(News news, List<NewsContentBlock> blocks) {
        return new NewsDetailAdminDto(
                news.getId(),
                news.getTitle(),
                news.getExcerpt(),
                news.getThumbnailFilename(),
                imageUrl(news.getThumbnailUrl(), news.getThumbnailFilename()),
                news.getThumbnailFocalPointX(),
                news.getThumbnailFocalPointY(),
                news.isPublished(),
                news.getPublishedAt(),
                news.getLanguage(),
                news.getTranslationGroupId(),
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
                imageUrl(block.getImageUrl(), block.getImageFilename()),
                block.getCaption(),
                block.getDisplayOrder()
        );
    }

    private @Nullable String imageUrl(@Nullable String externalUrl, @Nullable String filename) {
        return FileUrls.preferExternal(externalUrl, baseUrl, "news", filename);
    }

    private String normalizeVideoEmbedUrl(String inputUrl) {
        String embed = VideoEmbedUrls.toEmbedUrlOrNull(inputUrl);
        if (embed == null) {
            throw new LocalizedMessage.Rejected("error.video.url.unsupported");
        }
        return embed;
    }
}
