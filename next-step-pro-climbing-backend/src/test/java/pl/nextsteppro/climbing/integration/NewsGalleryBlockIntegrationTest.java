package pl.nextsteppro.climbing.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryDtos.AlbumArticleDto;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryService;
import pl.nextsteppro.climbing.api.news.NewsDtos.ContentBlockDto;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.news.BlockType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.news.NewsContentBlockRepository;
import pl.nextsteppro.climbing.domain.news.NewsRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deleting an album that articles embed, against the real schema: V107's ON DELETE SET NULL is
 * the whole mechanism, so only a database can say it works.
 */
class NewsGalleryBlockIntegrationTest extends BaseIntegrationTest {

    @Autowired private AlbumRepository albumRepository;
    @Autowired private NewsRepository newsRepository;
    @Autowired private NewsContentBlockRepository blockRepository;
    @Autowired private AdminGalleryService adminGalleryService;
    @Autowired private NewsService newsService;
    @Autowired private EntityManager entityManager;

    // Any non-null viewer: getPublishedById caches anonymous reads, and a cached detail from
    // before the delete would answer for the database.
    private static final UUID VIEWER = UUID.randomUUID();

    @Test
    void shouldNameEveryLanguageVersionThatEmbedsTheAlbum() {
        Album album = albumRepository.save(published(new Album("Obóz Frankenjura")));
        News pl = articleWithGallery("Relacja z obozu", "pl", album.getId());
        News en = articleWithGallery("Camp report", "en", album.getId());
        articleWithGallery("Inny wyjazd", "pl", albumRepository.save(new Album("Inny")).getId());

        List<AlbumArticleDto> articles = adminGalleryService.getAlbumArticles(album.getId());

        assertEquals(List.of(en.getId(), pl.getId()), articles.stream().map(AlbumArticleDto::newsId).toList(),
            "both language versions, sorted by title, and nothing that shows another album");
        assertEquals("en", articles.get(0).language());
    }

    @Test
    void shouldKeepTheArticleAndDropOnlyTheBlockWhenTheAlbumIsDeleted() {
        Album album = albumRepository.save(published(new Album("Obóz")));
        News news = articleWithGallery("Relacja z obozu", "pl", album.getId());
        NewsContentBlock text = new NewsContentBlock(news, BlockType.TEXT);
        text.setContent("Było świetnie.");
        text.setDisplayOrder(1);
        blockRepository.save(text);
        entityManager.flush();
        assertEquals(List.of("GALLERY", "TEXT"), publicBlockTypes(news.getId()));

        adminGalleryService.deleteAlbum(album.getId());
        entityManager.flush();
        entityManager.clear();

        assertTrue(newsRepository.existsById(news.getId()), "the article survives its album");
        List<NewsContentBlock> blocks = blockRepository.findByNewsIdOrderByDisplayOrderAsc(news.getId());
        assertEquals(2, blocks.size(), "the editor still has the block, to say its album is gone");
        assertEquals(BlockType.GALLERY, blocks.get(0).getBlockType());
        assertNull(blocks.get(0).getAlbumId());
        assertEquals(List.of("TEXT"), publicBlockTypes(news.getId()), "readers see the article without the block");
    }

    private List<String> publicBlockTypes(UUID newsId) {
        return newsService.getPublishedById(newsId, VIEWER).blocks().stream().map(ContentBlockDto::blockType).toList();
    }

    private News articleWithGallery(String title, String language, UUID albumId) {
        News news = new News(title);
        news.setLanguage(language);
        news.setPublished(true);
        news.setPublishedAt(Instant.now());
        news = newsRepository.save(news);
        NewsContentBlock block = new NewsContentBlock(news, BlockType.GALLERY);
        block.setAlbumId(albumId);
        block.setDisplayOrder(0);
        blockRepository.save(block);
        return news;
    }

    private static Album published(Album album) {
        album.setPublished(true);
        return album;
    }
}
