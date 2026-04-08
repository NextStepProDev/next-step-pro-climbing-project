package pl.nextsteppro.climbing.api.admin.news;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.news.AdminNewsDtos.*;
import pl.nextsteppro.climbing.domain.news.*;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.NewsletterMailService;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AdminNewsService — admin CMS operations for news management.
 *
 * Test coverage:
 * - Create / update / delete news
 * - Publish / unpublish
 * - Thumbnail upload, delete, URL set
 * - Text, image (file + URL), video embed blocks — add, update, delete
 * - Block move (UP/DOWN)
 * - Newsletter send (published vs draft guard)
 * - Video URL normalization (YouTube watch, youtu.be, Shorts, Instagram Reel/Post, invalid)
 */
@ExtendWith(MockitoExtension.class)
class AdminNewsServiceTest {

    @Mock private NewsRepository newsRepository;
    @Mock private NewsContentBlockRepository blockRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private UserRepository userRepository;
    @Mock private NewsletterMailService newsletterMailService;
    @Mock private MultipartFile mockFile;

    private AdminNewsService adminNewsService;

    private static final String BASE_URL = "https://nextsteppro.pl";

    private News testNews;
    private UUID newsId;

    @BeforeEach
    void setUp() {
        adminNewsService = new AdminNewsService(
                newsRepository, blockRepository, fileStorageService,
                userRepository, newsletterMailService, BASE_URL
        );

        newsId = UUID.randomUUID();
        testNews = new News("Test Article");
        setField(testNews, "id", newsId);
        setField(testNews, "createdAt", Instant.now());
        setField(testNews, "updatedAt", Instant.now());
    }

    // ========== CREATE ==========

    @Test
    void shouldCreateNewsSuccessfully() {
        // Given
        CreateNewsRequest request = new CreateNewsRequest("Nowy artykuł", "Zajawka");
        when(newsRepository.save(any(News.class))).thenAnswer(inv -> {
            News n = inv.getArgument(0);
            setField(n, "id", UUID.randomUUID());
            setField(n, "createdAt", Instant.now());
            setField(n, "updatedAt", Instant.now());
            return n;
        });

        // When
        NewsAdminDto result = adminNewsService.createNews(request);

        // Then
        assertNotNull(result);
        assertEquals("Nowy artykuł", result.title());
        assertEquals("Zajawka", result.excerpt());
        assertFalse(result.published());

        ArgumentCaptor<News> captor = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(captor.capture());
        assertEquals("Nowy artykuł", captor.getValue().getTitle());
    }

    // ========== UPDATE META ==========

    @Test
    void shouldUpdateNewsTitleAndExcerpt() {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateNewsMetaRequest request = new UpdateNewsMetaRequest("Zmieniony tytuł", "Nowa zajawka");

        // When
        NewsAdminDto result = adminNewsService.updateNewsMeta(newsId, request);

        // Then
        assertEquals("Zmieniony tytuł", result.title());
        assertEquals("Nowa zajawka", result.excerpt());
    }

    @Test
    void shouldUpdateOnlyTitleWhenExcerptIsNull() {
        // Given
        testNews.setExcerpt("Stara zajawka");
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminNewsService.updateNewsMeta(newsId, new UpdateNewsMetaRequest("Nowy tytuł", null));

        // Then
        assertEquals("Nowy tytuł", testNews.getTitle());
        assertEquals("Stara zajawka", testNews.getExcerpt()); // unchanged
    }

    // ========== PUBLISH / UNPUBLISH ==========

    @Test
    void shouldPublishNewsAndSetPublishedAt() {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        NewsAdminDto result = adminNewsService.setPublished(newsId, true);

        // Then
        assertTrue(result.published());
        assertNotNull(result.publishedAt());
    }

    @Test
    void shouldNotOverwritePublishedAtOnRepublish() {
        // Given
        Instant originalPublishedAt = Instant.parse("2025-01-01T10:00:00Z");
        setField(testNews, "published", true);
        setField(testNews, "publishedAt", originalPublishedAt);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminNewsService.setPublished(newsId, true);

        // Then
        assertEquals(originalPublishedAt, testNews.getPublishedAt());
    }

    @Test
    void shouldUnpublishNews() {
        // Given
        setField(testNews, "published", true);
        setField(testNews, "publishedAt", Instant.now());

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        NewsAdminDto result = adminNewsService.setPublished(newsId, false);

        // Then
        assertFalse(result.published());
    }

    // ========== DELETE NEWS ==========

    @Test
    void shouldDeleteNewsWithBlockImagesAndThumbnail() throws IOException {
        // Given
        String thumbnailFilename = "thumb.jpg";
        String blockImageFilename = "block-img.jpg";
        setField(testNews, "thumbnailFilename", thumbnailFilename);

        NewsContentBlock imageBlock = new NewsContentBlock(testNews, BlockType.IMAGE);
        setField(imageBlock, "id", UUID.randomUUID());
        setField(imageBlock, "imageFilename", blockImageFilename);

        NewsContentBlock textBlock = new NewsContentBlock(testNews, BlockType.TEXT);
        setField(textBlock, "id", UUID.randomUUID());

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of(imageBlock, textBlock));

        // When
        adminNewsService.deleteNews(newsId);

        // Then
        verify(fileStorageService).delete(blockImageFilename, "news");
        verify(fileStorageService).delete(thumbnailFilename, "news");
        verify(newsRepository).delete(testNews);
    }

    @Test
    void shouldDeleteNewsWithoutFilesWhenNonePresent() throws IOException {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of());

        // When
        adminNewsService.deleteNews(newsId);

        // Then
        verify(fileStorageService, never()).delete(any(), any());
        verify(newsRepository).delete(testNews);
    }

    @Test
    void shouldContinueDeletingNewsEvenWhenFileDeleteFails() throws IOException {
        // Given
        setField(testNews, "thumbnailFilename", "thumb.jpg");
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of());
        doThrow(new IOException("disk error")).when(fileStorageService).delete(any(), any());

        // When / Then — no exception thrown
        assertDoesNotThrow(() -> adminNewsService.deleteNews(newsId));
        verify(newsRepository).delete(testNews);
    }

    // ========== THUMBNAIL ==========

    @Test
    void shouldUploadThumbnailAndReplaceExistingOne() throws IOException {
        // Given
        String oldFilename = "old-thumb.jpg";
        String newFilename = "new-thumb.jpg";
        setField(testNews, "thumbnailFilename", oldFilename);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(fileStorageService.store(mockFile, "news")).thenReturn(newFilename);
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of());

        // When
        NewsDetailAdminDto result = adminNewsService.uploadThumbnail(newsId, mockFile);

        // Then
        verify(fileStorageService).delete(oldFilename, "news");
        verify(fileStorageService).store(mockFile, "news");
        assertEquals(BASE_URL + "/api/files/news/" + newFilename, result.thumbnailUrl());
    }

    @Test
    void shouldDeleteThumbnailFile() throws IOException {
        // Given
        String filename = "thumb.jpg";
        setField(testNews, "thumbnailFilename", filename);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminNewsService.deleteThumbnail(newsId);

        // Then
        verify(fileStorageService).delete(filename, "news");
        assertNull(testNews.getThumbnailFilename());
    }

    @Test
    void shouldThrowWhenDeletingThumbnailWhenNoneExists() {
        // Given — no thumbnailFilename and no thumbnailUrl on testNews
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));

        // When / Then
        assertThrows(IllegalStateException.class, () -> adminNewsService.deleteThumbnail(newsId));
    }

    @Test
    void shouldSetThumbnailUrlAndRemoveExistingFile() throws IOException {
        // Given
        setField(testNews, "thumbnailFilename", "old.jpg");
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(newsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminNewsService.setThumbnailUrl(newsId, new SetThumbnailUrlRequest("https://cdn.example.com/img.jpg"));

        // Then
        verify(fileStorageService).delete("old.jpg", "news");
        assertNull(testNews.getThumbnailFilename());
        assertEquals("https://cdn.example.com/img.jpg", testNews.getThumbnailUrl());
    }

    // ========== TEXT BLOCK ==========

    @Test
    void shouldAddTextBlock() {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(0);
        when(blockRepository.save(any())).thenAnswer(inv -> {
            NewsContentBlock b = inv.getArgument(0);
            setField(b, "id", UUID.randomUUID());
            return b;
        });

        // When
        ContentBlockAdminDto result = adminNewsService.addTextBlock(newsId, new AddTextBlockRequest("Treść bloku"));

        // Then
        assertEquals("TEXT", result.blockType());
        assertEquals("Treść bloku", result.content());
        assertEquals(1, result.displayOrder());
    }

    @Test
    void shouldUpdateTextBlockContent() {
        // Given
        NewsContentBlock block = new NewsContentBlock(testNews, BlockType.TEXT);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        setField(block, "content", "Stara treść");

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(blockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        adminNewsService.updateTextBlock(blockId, new UpdateTextBlockRequest("Nowa treść"));

        // Then
        assertEquals("Nowa treść", block.getContent());
    }

    @Test
    void shouldThrowWhenUpdatingTextBlockOnWrongType() {
        // Given
        NewsContentBlock block = new NewsContentBlock(testNews, BlockType.IMAGE);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> adminNewsService.updateTextBlock(blockId, new UpdateTextBlockRequest("x")));
    }

    // ========== IMAGE BLOCK ==========

    @Test
    void shouldAddImageBlockFromFile() throws IOException {
        // Given
        String filename = "img.jpg";
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(2);
        when(fileStorageService.store(mockFile, "news")).thenReturn(filename);
        when(blockRepository.save(any())).thenAnswer(inv -> {
            NewsContentBlock b = inv.getArgument(0);
            setField(b, "id", UUID.randomUUID());
            return b;
        });

        // When
        UploadBlockImageResponse result = adminNewsService.addImageBlock(newsId, mockFile, "Podpis");

        // Then
        assertEquals(filename, result.imageFilename());
        assertEquals(BASE_URL + "/api/files/news/" + filename, result.imageUrl());
        assertEquals(3, result.displayOrder());
    }

    @Test
    void shouldAddImageBlockFromUrl() {
        // Given
        String imageUrl = "https://cdn.example.com/photo.jpg";
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(0);
        when(blockRepository.save(any())).thenAnswer(inv -> {
            NewsContentBlock b = inv.getArgument(0);
            setField(b, "id", UUID.randomUUID());
            return b;
        });

        // When
        ContentBlockAdminDto result = adminNewsService.addImageBlockFromUrl(newsId,
                new AddImageBlockFromUrlRequest(imageUrl, "Podpis CDN"));

        // Then
        assertEquals("IMAGE", result.blockType());
        assertEquals(imageUrl, result.imageUrl());
        assertEquals("Podpis CDN", result.caption());
    }

    @Test
    void shouldDeleteImageBlockAndItsFile() throws IOException {
        // Given
        String filename = "block-img.jpg";
        NewsContentBlock block = new NewsContentBlock(testNews, BlockType.IMAGE);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        setField(block, "imageFilename", filename);

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When
        adminNewsService.deleteBlock(blockId);

        // Then
        verify(fileStorageService).delete(filename, "news");
        verify(blockRepository).delete(block);
    }

    @Test
    void shouldDeleteTextBlockWithoutFileDeletion() throws IOException {
        // Given
        NewsContentBlock block = new NewsContentBlock(testNews, BlockType.TEXT);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        setField(block, "content", "Treść");

        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When
        adminNewsService.deleteBlock(blockId);

        // Then
        verify(fileStorageService, never()).delete(any(), any());
        verify(blockRepository).delete(block);
    }

    // ========== VIDEO EMBED BLOCK ==========

    @Test
    void shouldAddVideoEmbedBlockWithNormalizedYouTubeUrl() {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(0);
        when(blockRepository.save(any())).thenAnswer(inv -> {
            NewsContentBlock b = inv.getArgument(0);
            setField(b, "id", UUID.randomUUID());
            return b;
        });

        // When
        ContentBlockAdminDto result = adminNewsService.addVideoEmbedBlock(newsId,
                new AddVideoEmbedBlockRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        // Then
        assertEquals("VIDEO_EMBED", result.blockType());
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", result.content());
    }

    @Test
    void shouldThrowWhenUpdatingVideoEmbedOnWrongType() {
        // Given
        NewsContentBlock block = new NewsContentBlock(testNews, BlockType.TEXT);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> adminNewsService.updateVideoEmbedBlock(blockId,
                        new UpdateVideoEmbedBlockRequest("https://youtu.be/dQw4w9WgXcQ")));
    }

    // ========== MOVE BLOCK ==========

    @Test
    void shouldMoveBlockUp() {
        // Given
        NewsContentBlock block1 = buildBlock(BlockType.TEXT, 1);
        NewsContentBlock block2 = buildBlock(BlockType.TEXT, 2);
        UUID block2Id = block2.getId();

        when(blockRepository.findById(block2Id)).thenReturn(Optional.of(block2));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of(block1, block2));

        // When
        adminNewsService.moveBlock(block2Id, "UP");

        // Then
        assertEquals(1, block2.getDisplayOrder());
        assertEquals(2, block1.getDisplayOrder());
        verify(blockRepository, times(2)).save(any());
    }

    @Test
    void shouldMoveBlockDown() {
        // Given
        NewsContentBlock block1 = buildBlock(BlockType.TEXT, 1);
        NewsContentBlock block2 = buildBlock(BlockType.TEXT, 2);
        UUID block1Id = block1.getId();

        when(blockRepository.findById(block1Id)).thenReturn(Optional.of(block1));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of(block1, block2));

        // When
        adminNewsService.moveBlock(block1Id, "DOWN");

        // Then
        assertEquals(2, block1.getDisplayOrder());
        assertEquals(1, block2.getDisplayOrder());
    }

    @Test
    void shouldNotMoveFirstBlockUp() {
        // Given
        NewsContentBlock block1 = buildBlock(BlockType.TEXT, 1);
        NewsContentBlock block2 = buildBlock(BlockType.TEXT, 2);
        UUID block1Id = block1.getId();

        when(blockRepository.findById(block1Id)).thenReturn(Optional.of(block1));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of(block1, block2));

        // When
        adminNewsService.moveBlock(block1Id, "UP");

        // Then — display orders unchanged
        assertEquals(1, block1.getDisplayOrder());
        assertEquals(2, block2.getDisplayOrder());
        verify(blockRepository, never()).save(any());
    }

    // ========== NEWSLETTER ==========

    @Test
    void shouldSendNewsletterToAllSubscribers() {
        // Given
        setField(testNews, "published", true);
        setField(testNews, "publishedAt", Instant.now());

        User subscriber = mock(User.class);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(newsId)).thenReturn(List.of());
        when(userRepository.findAllByNewsletterSubscribedTrue()).thenReturn(List.of(subscriber));

        // When
        NewsletterSentDto result = adminNewsService.sendNewsNewsletter(newsId);

        // Then
        assertEquals(1, result.subscriberCount());
        verify(newsletterMailService).sendToAll(eq(testNews), anyList(), eq(List.of(subscriber)), eq(BASE_URL));
    }

    @Test
    void shouldThrowWhenSendingNewsletterForUnpublishedNews() {
        // Given — testNews is unpublished by default
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));

        // When / Then
        assertThrows(IllegalStateException.class, () -> adminNewsService.sendNewsNewsletter(newsId));
        verify(newsletterMailService, never()).sendToAll(any(), any(), any(), any());
    }

    // ========== VIDEO URL NORMALIZATION ==========

    @Test
    void shouldNormalizeYouTubeWatchUrl() {
        assertVideoEmbed("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @Test
    void shouldNormalizeYouTubeShortUrl() {
        assertVideoEmbed("https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @Test
    void shouldNormalizeYouTubeShortsUrl() {
        assertVideoEmbed("https://www.youtube.com/shorts/dQw4w9WgXcQ",
                "https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @Test
    void shouldNormalizeInstagramReelUrl() {
        assertVideoEmbed("https://www.instagram.com/reel/ABC123def/",
                "https://www.instagram.com/reel/ABC123def/embed/");
    }

    @Test
    void shouldNormalizeInstagramPostUrl() {
        assertVideoEmbed("https://www.instagram.com/p/ABC123def/",
                "https://www.instagram.com/p/ABC123def/embed/");
    }

    @Test
    void shouldThrowForUnsupportedVideoUrl() {
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> adminNewsService.addVideoEmbedBlock(newsId,
                        new AddVideoEmbedBlockRequest("https://vimeo.com/123456789")));
    }

    // ========== NOT FOUND GUARD ==========

    @Test
    void shouldThrowWhenNewsNotFound() {
        // Given
        when(newsRepository.findById(newsId)).thenReturn(Optional.empty());

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> adminNewsService.getNews(newsId));
    }

    // ========== HELPERS ==========

    /** Triggers video normalization via addVideoEmbedBlock and checks the stored embed URL. */
    private void assertVideoEmbed(String inputUrl, String expectedEmbedUrl) {
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(testNews));
        when(blockRepository.findMaxDisplayOrder(newsId)).thenReturn(0);
        when(blockRepository.save(any())).thenAnswer(inv -> {
            NewsContentBlock b = inv.getArgument(0);
            setField(b, "id", UUID.randomUUID());
            return b;
        });

        ContentBlockAdminDto result = adminNewsService.addVideoEmbedBlock(newsId,
                new AddVideoEmbedBlockRequest(inputUrl));
        assertEquals(expectedEmbedUrl, result.content());
    }

    private NewsContentBlock buildBlock(BlockType type, int displayOrder) {
        NewsContentBlock block = new NewsContentBlock(testNews, type);
        UUID blockId = UUID.randomUUID();
        setField(block, "id", blockId);
        setField(block, "displayOrder", displayOrder);
        return block;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }
}
