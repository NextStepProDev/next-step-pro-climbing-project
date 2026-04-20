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
import pl.nextsteppro.climbing.domain.news.NewsStar;
import pl.nextsteppro.climbing.domain.news.NewsStarId;
import pl.nextsteppro.climbing.domain.news.NewsStarRepository;
import pl.nextsteppro.climbing.domain.news.NewsSummaryProjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * - Search by title (unaccent normalization)
 * - Starred filter per user
 * - star/unstar operations (idempotent)
 * - starred field in DTOs based on authentication state
 */
@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @Mock
    private NewsContentBlockRepository blockRepository;

    @Mock
    private NewsStarRepository newsStarRepository;

    private NewsService newsService;

    private static final String BASE_URL = "https://nextsteppro.pl";

    @BeforeEach
    void setUp() {
        newsService = new NewsService(newsRepository, blockRepository, newsStarRepository, BASE_URL);
    }

    // ========== getAllPublished — base (unauthenticated, no filters) ==========

    @Test
    void shouldReturnPaginatedPublishedNews() {
        // Given
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Test Article", "Excerpt", null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(0, result.page());
        assertEquals(12, result.size());
        assertEquals(1L, result.totalElements());
        assertFalse(result.hasNext());

        verify(newsRepository).findAllPublishedSummaries(PageRequest.of(0, 12));
        verify(newsStarRepository, never()).findNewsIdsByIdUserId(any());
    }

    @Test
    void shouldReturnEmptyPageWhenNoPublishedNews() {
        // Given
        var emptyPage = new PageImpl<NewsSummaryProjection>(List.of(), PageRequest.of(0, 12), 0);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(emptyPage);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

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
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

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
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

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
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

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
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

        // Then
        assertNull(result.content().get(0).thumbnailUrl());
    }

    @Test
    void shouldReturnNullStarredFieldWhenUnauthenticated() {
        // Given
        NewsSummaryProjection projection = mockSummaryProjection(UUID.randomUUID(), "Article", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, null);

        // Then
        assertNull(result.content().get(0).starred());
    }

    // ========== getAllPublished — search ==========

    @Test
    void shouldUseTitleSearchQueryWhenQProvided() {
        // Given
        String q = "wspinaczka";
        UUID newsId = UUID.randomUUID();
        NewsSummaryProjection projection = mockSummaryProjection(newsId, "Wspinaczka górska", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);
        when(newsRepository.findAllPublishedSummariesByTitle(eq(q), any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, q, false, null);

        // Then
        assertEquals(1, result.content().size());
        verify(newsRepository).findAllPublishedSummariesByTitle(eq(q), any());
        verify(newsRepository, never()).findAllPublishedSummaries(any());
    }

    @Test
    void shouldReturnEmptyPageWhenSearchMatchesNothing() {
        // Given
        String q = "nieistniejący tytuł";
        var emptyPage = new PageImpl<NewsSummaryProjection>(List.of(), PageRequest.of(0, 12), 0);
        when(newsRepository.findAllPublishedSummariesByTitle(eq(q), any())).thenReturn(emptyPage);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, q, false, null);

        // Then
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void shouldFallBackToBaseQueryWhenQIsBlank() {
        // Given — blank query should behave like no filter
        var page = new PageImpl<NewsSummaryProjection>(List.of(), PageRequest.of(0, 12), 0);
        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);

        // When
        newsService.getAllPublished(0, 12, "   ", false, null);

        // Then
        verify(newsRepository).findAllPublishedSummaries(any());
        verify(newsRepository, never()).findAllPublishedSummariesByTitle(any(), any());
    }

    // ========== getAllPublished — starred filter ==========

    @Test
    void shouldReturnEmptyPageWhenStarredFilterButNoUserId() {
        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, null);

        // Then
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
        verify(newsRepository, never()).findAllPublishedSummaries(any());
        verify(newsRepository, never()).findAllPublishedSummariesByIds(any(), any());
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoStarredNews() {
        // Given
        UUID userId = UUID.randomUUID();
        when(newsStarRepository.findNewsIdsByIdUserId(userId)).thenReturn(Set.of());

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, userId);

        // Then
        assertTrue(result.content().isEmpty());
        verify(newsRepository, never()).findAllPublishedSummariesByIds(any(), any());
    }

    @Test
    void shouldReturnOnlyStarredNewsForUser() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID starredId = UUID.randomUUID();
        Set<UUID> starredIds = Set.of(starredId);

        NewsSummaryProjection projection = mockSummaryProjection(starredId, "Starred Article", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);

        when(newsStarRepository.findNewsIdsByIdUserId(userId)).thenReturn(starredIds);
        when(newsRepository.findAllPublishedSummariesByIds(eq(starredIds), any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, userId);

        // Then
        assertEquals(1, result.content().size());
        assertTrue(result.content().get(0).starred());
        verify(newsRepository).findAllPublishedSummariesByIds(eq(starredIds), any());
    }

    @Test
    void shouldUseCombinedQueryWhenStarredAndSearchBothActive() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID starredId = UUID.randomUUID();
        Set<UUID> starredIds = Set.of(starredId);
        String q = "wspinaczka";

        NewsSummaryProjection projection = mockSummaryProjection(starredId, "Wspinaczka", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(projection), PageRequest.of(0, 12), 1);

        when(newsStarRepository.findNewsIdsByIdUserId(userId)).thenReturn(starredIds);
        when(newsRepository.findAllPublishedSummariesByTitleAndIds(eq(q), eq(starredIds), any())).thenReturn(page);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, q, true, userId);

        // Then
        assertEquals(1, result.content().size());
        verify(newsRepository).findAllPublishedSummariesByTitleAndIds(eq(q), eq(starredIds), any());
    }

    @Test
    void shouldOverlayStarredStatusWhenAuthenticatedWithoutStarFilter() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID starredNewsId = UUID.randomUUID();
        UUID unstarredNewsId = UUID.randomUUID();

        NewsSummaryProjection starred = mockSummaryProjection(starredNewsId, "Starred", null, null, null, Instant.now());
        NewsSummaryProjection notStarred = mockSummaryProjection(unstarredNewsId, "Not starred", null, null, null, Instant.now());
        var page = new PageImpl<>(List.of(starred, notStarred), PageRequest.of(0, 12), 2);

        when(newsRepository.findAllPublishedSummaries(any())).thenReturn(page);
        when(newsStarRepository.findNewsIdsByIdUserId(userId)).thenReturn(Set.of(starredNewsId));

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, userId);

        // Then
        NewsSummaryDto starredDto = result.content().stream()
                .filter(d -> d.id().equals(starredNewsId)).findFirst().orElseThrow();
        NewsSummaryDto notStarredDto = result.content().stream()
                .filter(d -> d.id().equals(unstarredNewsId)).findFirst().orElseThrow();

        assertTrue(starredDto.starred());
        assertFalse(notStarredDto.starred());
    }

    // ========== getPublishedById ==========

    @Test
    void shouldReturnNewsDetailSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Published Article", "Short excerpt");

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());

        // When
        NewsDetailDto result = newsService.getPublishedById(id, null);

        // Then
        assertNotNull(result);
        assertEquals(id, result.id());
        assertEquals("Published Article", result.title());
        assertEquals("Short excerpt", result.excerpt());
        assertTrue(result.blocks().isEmpty());
        assertNull(result.starred());
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
        NewsDetailDto result = newsService.getPublishedById(id, null);

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
        NewsDetailDto result = newsService.getPublishedById(id, null);

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
        NewsDetailDto result = newsService.getPublishedById(id, null);

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
            () -> newsService.getPublishedById(id, null)
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
            () -> newsService.getPublishedById(id, null)
        );
        assertEquals("News not found", exception.getMessage());

        verify(blockRepository, never()).findByNewsIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void shouldReturnStarredTrueInDetailWhenUserStarredArticle() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        News news = buildPublishedNews(id, "Published Article", null);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());
        when(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, id)).thenReturn(true);

        // When
        NewsDetailDto result = newsService.getPublishedById(id, userId);

        // Then
        assertTrue(result.starred());
    }

    @Test
    void shouldReturnStarredFalseInDetailWhenUserHasNotStarredArticle() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        News news = buildPublishedNews(id, "Published Article", null);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());
        when(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, id)).thenReturn(false);

        // When
        NewsDetailDto result = newsService.getPublishedById(id, userId);

        // Then
        assertFalse(result.starred());
    }

    @Test
    void shouldReturnNullStarredInDetailWhenUnauthenticated() {
        // Given
        UUID id = UUID.randomUUID();
        News news = buildPublishedNews(id, "Published Article", null);

        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        when(blockRepository.findByNewsIdOrderByDisplayOrderAsc(id)).thenReturn(List.of());

        // When
        NewsDetailDto result = newsService.getPublishedById(id, null);

        // Then
        assertNull(result.starred());
        verify(newsStarRepository, never()).existsByIdUserIdAndIdNewsId(any(), any());
    }

    // ========== starNews / unstarNews ==========

    @Test
    void shouldStarNewsWhenNotAlreadyStarred() {
        // Given
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        News news = buildPublishedNews(newsId, "Article", null);
        NewsStarId starId = new NewsStarId(userId, newsId);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(newsStarRepository.existsById(starId)).thenReturn(false);

        // When
        newsService.starNews(newsId, userId);

        // Then
        verify(newsStarRepository).save(any(NewsStar.class));
    }

    @Test
    void shouldBeIdempotentWhenStarringAlreadyStarredNews() {
        // Given
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        News news = buildPublishedNews(newsId, "Article", null);
        NewsStarId starId = new NewsStarId(userId, newsId);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(newsStarRepository.existsById(starId)).thenReturn(true);

        // When — no exception
        assertDoesNotThrow(() -> newsService.starNews(newsId, userId));

        // Then — save NOT called again
        verify(newsStarRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenStarringUnpublishedNews() {
        // Given
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        News draft = new News("Draft");
        setFieldViaReflection(draft, "id", newsId);

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(draft));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> newsService.starNews(newsId, userId));
        verify(newsStarRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenStarringNonExistentNews() {
        // Given
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(newsRepository.findById(newsId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> newsService.starNews(newsId, userId));
        verify(newsStarRepository, never()).save(any());
    }

    @Test
    void shouldUnstarNews() {
        // Given
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // When
        newsService.unstarNews(newsId, userId);

        // Then
        verify(newsStarRepository).deleteByIdUserIdAndIdNewsId(userId, newsId);
    }

    @Test
    void shouldBeNoOpWhenUnstarringNotStarredNews() {
        // Given — deleteByIdUserIdAndIdNewsId is called regardless; it's a no-op if not present
        UUID newsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // When — no exception
        assertDoesNotThrow(() -> newsService.unstarNews(newsId, userId));

        // Then
        verify(newsStarRepository).deleteByIdUserIdAndIdNewsId(userId, newsId);
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
