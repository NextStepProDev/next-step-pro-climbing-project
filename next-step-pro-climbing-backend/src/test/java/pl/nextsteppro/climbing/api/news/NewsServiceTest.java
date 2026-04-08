package pl.nextsteppro.climbing.api.news;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pl.nextsteppro.climbing.api.news.NewsDtos.*;
import pl.nextsteppro.climbing.domain.news.BlockType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.news.NewsContentBlockRepository;
import pl.nextsteppro.climbing.domain.news.NewsRepository;
import pl.nextsteppro.climbing.domain.news.NewsSummaryProjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for NewsService — public CMS news listing and detail.
 *
 * Test coverage:
 * - Paginated listing of published news
 * - Detail retrieval with content blocks
 * - Draft (unpublished) news is hidden from public
 * - Thumbnail URL resolution (direct URL vs file-based)
 * - Block image URL resolution (direct URL vs file-based)
 */
@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @Mock
    private NewsContentBlockRepository blockRepository;

    private NewsService newsService;

    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        newsService = new NewsService(newsRepository, blockRepository, BASE_URL);
    }

    // ========== getAllPublished TESTS ==========

    @Test
    void shouldReturnPaginatedPublishedNews() {
        // Given
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Test Article", "Excerpt", null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(0, result.page());
        assertEquals(12, result.size());
        assertEquals(1L, result.totalElements());
        assertFalse(result.hasNext());

        verify(newsRepository).findAllPublishedSummaries(PageRequest.of(0, 12));
    }

    @Test
    void shouldReturnEmptyPageWhenNoPublishedNews() {
        // Given
        var emptyPage = new PageImpl<NewsSummaryProjection>(List.of(), PageRequest.of(0, 12), 0);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(emptyPage);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        assertNotNull(result);
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
        assertFalse(result.hasNext());
    }

    @Test
    void shouldIndicateHasNextWhenMorePagesExist() {
        // Given
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Article", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 25);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        assertTrue(result.hasNext());
        assertEquals(25L, result.totalElements());
    }

    @Test
    void shouldBuildThumbnailUrlFromFilenameWhenNoDirectUrl() {
        // Given
        String filename = "abc123.jpg";
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Article", null, filename, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        NewsSummaryDto dto = result.content().get(0);
        assertEquals(BASE_URL + "/api/files/news/" + filename, dto.thumbnailUrl());
    }

    @Test
    void shouldBuildThumbnailUrlFromDirectUrlWhenAvailable() {
        // Given
        String directUrl = "https://cdn.example.com/image.jpg";
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Article", null, "file.jpg", directUrl, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        NewsSummaryDto dto = result.content().get(0);
        assertEquals(directUrl, dto.thumbnailUrl());
    }

    @Test
    void shouldReturnNullThumbnailWhenNeitherFileNorUrlPresent() {
        // Given
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Article", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12);

        // Then
        assertNull(result.content().get(0).thumbnailUrl());
    }

    // ========== getPublishedById TESTS ==========

    @Test
    void shouldReturnNewsDetailSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Published Article", "Short excerpt");

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());

        // When
        NewsDetailDto result = newsService.getPublishedById(id);

        // Then
        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals("Published Article", result.title());
        assertEquals("Short excerpt", result.excerpt());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void shouldReturnNewsDetailWithContentBlocks() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Article with Blocks", null);

        NewsContentBlock textBlock = new NewsContentBlock(news, BlockType.TEXT);
        setFieldViaReflection(textBlock, "id", UUID.randomUUID());
        setFieldViaReflection(textBlock, "content", "Hello world");
        setFieldViaReflection(textBlock, "displayOrder", 1);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of(textBlock));

        // When
        NewsDetailDto result = newsService.getPublishedById(id);

        // Then
        assertNotNull(result);
        assertEquals(1, result.blocks().size());
        ContentBlockDto block = result.blocks().get(0);
        assertEquals("TEXT", block.blockType());
        assertEquals("Hello world", block.content());
        assertEquals(1, block.displayOrder());
    }

    @Test
    void shouldResolveBlockImageUrlFromDirectUrlFirst() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Article", null);

        String directUrl = "https://cdn.example.com/block-image.jpg";
        NewsContentBlock imageBlock = new NewsContentBlock(news, BlockType.IMAGE);
        setFieldViaReflection(imageBlock, "id", UUID.randomUUID());
        setFieldViaReflection(imageBlock, "imageUrl", directUrl);
        setFieldViaReflection(imageBlock, "imageFilename", "local-file.jpg");
        setFieldViaReflection(imageBlock, "displayOrder", 1);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of(imageBlock));

        // When
        NewsDetailDto result = newsService.getPublishedById(id);

        // Then
        assertEquals(directUrl, result.blocks().get(0).imageUrl());
    }

    @Test
    void shouldResolveBlockImageUrlFromFilenameWhenNoDirectUrl() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Article", null);
        String filename = "block-image.jpg";

        NewsContentBlock imageBlock = new NewsContentBlock(news, BlockType.IMAGE);
        setFieldViaReflection(imageBlock, "id", UUID.randomUUID());
        setFieldViaReflection(imageBlock, "imageFilename", filename);
        setFieldViaReflection(imageBlock, "displayOrder", 1);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of(imageBlock));

        // When
        NewsDetailDto result = newsService.getPublishedById(id);

        // Then
        assertEquals(BASE_URL + "/api/files/news/" + filename, result.blocks().get(0).imageUrl());
    }

    @Test
    void shouldThrowExceptionWhenNewsNotFound() {
        // Given
        UUID id = UUID.randomUUID();
        when(newsRepository.findById(id)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> newsService.getPublishedById(id)
        );
        assertEquals("News not found", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNewsIsNotPublished() {
        // Given
        UUID id = UUID.randomUUID();
        News draftNews = new News("Draft Article");
        setFieldViaReflection(draftNews, "id", id);
        // published = false (default)

        when(newsRepository.findById(id)).thenReturn(Optional.of(draftNews));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> newsService.getPublishedById(id)
        );
        assertEquals("News not found", exception.getMessage());

        // Blocks should NOT be loaded for unpublished news
        verify(blockRepository, never()).findByNewsIdOrderByDisplayOrderAsc(any());
    }

    // ========== HELPER METHODS ==========

    private NewsSummaryProjection mockSummaryProjection(UUID id, String title, String excerpt,
                                                         String filename, String url, Instant publishedAt) {
        NewsSummaryProjection projection = mock(NewsSummaryProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getTitle()).thenReturn(title);
        when(projection.getExcerpt()).thenReturn(excerpt);
        when(projection.getThumbnailFilename()).thenReturn(filename);
        when(projection.getThumbnailUrl()).thenReturn(url);
        when(projection.getThumbnailFocalPointX()).thenReturn(null);
        when(projection.getThumbnailFocalPointY()).thenReturn(null);
        when(projection.getPublishedAt()).thenReturn(publishedAt);
        return projection;
    }

    private News buildPublishedNews(UUID id, String title, String excerpt) {
        News news = new News(title);
        setFieldViaReflection(news, "id", id);
        setFieldViaReflection(news, "excerpt", excerpt);
        setFieldViaReflection(news, "published", true);
        setFieldViaReflection(news, "publishedAt", Instant.now());
        setFieldViaReflection(news, "createdAt", Instant.now());
        setFieldViaReflection(news, "updatedAt", Instant.now());
        return news;
    }

    private void setFieldViaReflection(Object target, String fieldName, Object value) {
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
