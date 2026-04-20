package pl.nextsteppro.climbing.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsPageDto;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsSummaryDto;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsRepository;
import pl.nextsteppro.climbing.domain.news.NewsStarRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for news starring and search features.
 *
 * Tests use a real PostgreSQL database (Testcontainers) with the full Flyway migration
 * including V41 (unaccent extension + user_news_stars table).
 *
 * Coverage:
 * - Star / unstar flow
 * - Starred filter
 * - Title search with unaccent (Polish character normalization)
 * - Combined starred + search
 * - Idempotency (double-star, unstar not-starred)
 * - Cascade delete (news → stars, user → stars)
 * - Edge cases: empty query, no results, unauthenticated starred filter
 */
class NewsStarIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private NewsStarRepository newsStarRepository;

    @Autowired
    private NewsService newsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        newsStarRepository.deleteAll();
        newsRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(buildUser("user@example.com"));
    }

    // ========== STAR / UNSTAR ==========

    @Test
    void shouldStarAndUnstarNews() {
        // Given
        News news = savePublishedNews("Article about climbing");
        UUID newsId = news.getId();
        UUID userId = testUser.getId();

        // When: star
        newsService.starNews(newsId, userId);

        // Then: star exists
        assertTrue(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));

        // When: unstar
        newsService.unstarNews(newsId, userId);

        // Then: star removed
        assertFalse(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));
    }

    @Test
    void shouldBeIdempotentDoublestar() {
        // Given
        News news = savePublishedNews("Article");
        UUID newsId = news.getId();
        UUID userId = testUser.getId();

        // When: star twice
        newsService.starNews(newsId, userId);
        assertDoesNotThrow(() -> newsService.starNews(newsId, userId));

        // Then: exactly one star record
        Set<UUID> starredIds = newsStarRepository.findNewsIdsByIdUserId(userId);
        assertEquals(1, starredIds.size());
    }

    @Test
    void shouldBeNoOpWhenUnstarringNotStarredNews() {
        // Given
        News news = savePublishedNews("Article");
        UUID newsId = news.getId();
        UUID userId = testUser.getId();

        // When — no star exists, unstar is silent
        assertDoesNotThrow(() -> newsService.unstarNews(newsId, userId));

        // Then: no records
        assertFalse(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));
    }

    @Test
    void shouldThrowWhenStarringNonExistentNews() {
        // Given — non-existent news ID
        UUID fakeId = UUID.randomUUID();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> newsService.starNews(fakeId, testUser.getId()));
    }

    @Test
    void shouldThrowWhenStarringUnpublishedNews() {
        // Given
        News draft = new News("Draft Article");
        draft = newsRepository.save(draft);
        UUID draftId = draft.getId();

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> newsService.starNews(draftId, testUser.getId()));
    }

    // ========== CASCADE DELETE ==========

    @Test
    void shouldDeleteStarWhenNewsDeleted() {
        // Given
        News news = savePublishedNews("Article to be deleted");
        UUID newsId = news.getId();
        UUID userId = testUser.getId();
        newsService.starNews(newsId, userId);
        assertTrue(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));

        // When: delete news
        newsRepository.deleteById(newsId);
        newsRepository.flush();

        // Then: star cascade-deleted
        assertFalse(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));
    }

    @Test
    void shouldDeleteStarWhenUserDeleted() {
        // Given
        News news = savePublishedNews("Article");
        UUID newsId = news.getId();
        UUID userId = testUser.getId();
        newsService.starNews(newsId, userId);
        assertTrue(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));

        // When: delete user
        userRepository.deleteById(userId);
        userRepository.flush();

        // Then: star cascade-deleted
        assertFalse(newsStarRepository.existsByIdUserIdAndIdNewsId(userId, newsId));
    }

    // ========== STARRED FILTER ==========

    @Test
    void shouldFilterAndReturnOnlyStarredNews() {
        // Given
        News starred1 = savePublishedNews("Starred Article 1");
        News starred2 = savePublishedNews("Starred Article 2");
        News notStarred = savePublishedNews("Not Starred");

        newsService.starNews(starred1.getId(), testUser.getId());
        newsService.starNews(starred2.getId(), testUser.getId());

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, testUser.getId());

        // Then
        assertEquals(2, result.content().size());
        List<UUID> returnedIds = result.content().stream().map(NewsSummaryDto::id).toList();
        assertTrue(returnedIds.contains(starred1.getId()));
        assertTrue(returnedIds.contains(starred2.getId()));
        assertFalse(returnedIds.contains(notStarred.getId()));
    }

    @Test
    void shouldReturnEmptyPageWhenStarredFilterButNoStars() {
        // Given: user has no starred articles
        savePublishedNews("Some Article");

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, testUser.getId());

        // Then
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void shouldReturnEmptyPageWhenStarredFilterButUnauthenticated() {
        // Given
        savePublishedNews("Some Article");

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, null);

        // Then
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void shouldIncludeStarredFlagInResultsForAuthenticatedUser() {
        // Given
        News starredNews = savePublishedNews("Starred");
        News notStarredNews = savePublishedNews("Not Starred");
        newsService.starNews(starredNews.getId(), testUser.getId());

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, null, false, testUser.getId());

        // Then
        NewsSummaryDto starredDto = result.content().stream()
                .filter(d -> d.id().equals(starredNews.getId()))
                .findFirst().orElseThrow();
        NewsSummaryDto notStarredDto = result.content().stream()
                .filter(d -> d.id().equals(notStarredNews.getId()))
                .findFirst().orElseThrow();

        assertTrue(starredDto.starred());
        assertFalse(notStarredDto.starred());
    }

    @Test
    void shouldIsolateStarsPerUser() {
        // Given
        User otherUser = userRepository.save(buildUser("other@example.com"));
        News news = savePublishedNews("Shared Article");

        newsService.starNews(news.getId(), testUser.getId());

        // When: other user views
        NewsPageDto result = newsService.getAllPublished(0, 12, null, true, otherUser.getId());

        // Then: other user sees no starred
        assertTrue(result.content().isEmpty());
    }

    // ========== SEARCH ==========

    @Test
    void shouldSearchNewsByTitleCaseInsensitive() {
        // Given
        savePublishedNews("Wspinaczka górska");
        savePublishedNews("Kurs nurkowania");

        // When: lowercase query
        NewsPageDto result = newsService.getAllPublished(0, 12, "wspinaczka", false, null);

        // Then
        assertEquals(1, result.content().size());
        assertEquals("Wspinaczka górska", result.content().get(0).title());
    }

    @Test
    void shouldNormalizePolishCharactersInSearch() {
        // Given: title with Polish ą
        savePublishedNews("Wspinaczka na ścianie");

        // When: query without Polish diacritic (a instead of ą, s instead of ś)
        NewsPageDto result = newsService.getAllPublished(0, 12, "scianie", false, null);

        // Then: unaccent matches
        assertEquals(1, result.content().size());
    }

    @Test
    void shouldNormalizePolishCharsInTitle() {
        // Given: query with Polish chars, title with normalized equivalents
        savePublishedNews("Wspinanczka na scianie");

        // When: query with proper Polish diacritic
        NewsPageDto result = newsService.getAllPublished(0, 12, "ścianie", false, null);

        // Then: both sides normalized → match
        assertEquals(1, result.content().size());
    }

    @Test
    void shouldReturnEmptyWhenSearchMatchesNothing() {
        // Given
        savePublishedNews("Artykuł o wspinaczce");

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, "nurkowanie", false, null);

        // Then
        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void shouldReturnAllWhenQueryIsBlank() {
        // Given
        savePublishedNews("Article A");
        savePublishedNews("Article B");

        // When: blank query = no filter
        NewsPageDto result = newsService.getAllPublished(0, 12, "  ", false, null);

        // Then
        assertEquals(2, result.content().size());
    }

    @Test
    void shouldSearchOnlyPublishedNews() {
        // Given
        savePublishedNews("Published Wspinaczka");
        News draft = new News("Draft Wspinaczka");
        newsRepository.save(draft);

        // When
        NewsPageDto result = newsService.getAllPublished(0, 12, "Wspinaczka", false, null);

        // Then: draft not included
        assertEquals(1, result.content().size());
    }

    @Test
    void shouldMatchPartialTitle() {
        // Given
        savePublishedNews("Zaawansowana wspinaczka skalna");

        // When: partial word from middle of title
        NewsPageDto result = newsService.getAllPublished(0, 12, "skalna", false, null);

        // Then
        assertEquals(1, result.content().size());
    }

    // ========== COMBINED SEARCH + STARRED ==========

    @Test
    void shouldCombineStarredFilterAndSearch() {
        // Given
        News starredMatch = savePublishedNews("Wspinaczka skalna");
        News starredNoMatch = savePublishedNews("Inny tytuł");
        savePublishedNews("Wspinaczka lodowa"); // not starred, should not appear even though title matches

        newsService.starNews(starredMatch.getId(), testUser.getId());
        newsService.starNews(starredNoMatch.getId(), testUser.getId());

        // When: search "wspinaczka" among starred only
        NewsPageDto result = newsService.getAllPublished(0, 12, "wspinaczka", true, testUser.getId());

        // Then: only starredMatch qualifies (starred AND title matches)
        assertEquals(1, result.content().size());
        assertEquals(starredMatch.getId(), result.content().get(0).id());
    }

    // ========== HELPER METHODS ==========

    private News savePublishedNews(String title) {
        News news = new News(title);
        setPublished(news, true);
        return newsRepository.save(news);
    }

    private void setPublished(News news, boolean published) {
        try {
            var f = news.getClass().getDeclaredField("published");
            f.setAccessible(true);
            f.set(news, published);

            var publishedAt = news.getClass().getDeclaredField("publishedAt");
            publishedAt.setAccessible(true);
            publishedAt.set(news, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User buildUser(String email) {
        User user = new User(email, "Test", "User", "+48123456789", email.split("@")[0]);
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return user;
    }
}
