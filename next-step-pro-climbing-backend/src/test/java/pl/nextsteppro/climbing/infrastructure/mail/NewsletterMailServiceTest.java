package pl.nextsteppro.climbing.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.news.BlockType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsletterMailServiceTest {

    private static final String BASE = "https://nextsteppro.pl";

    private final MailDispatcher dispatcher = mock(MailDispatcher.class);
    private final UserService userService = mock(UserService.class);
    private final MessageService msg = mock(MessageService.class);
    private final NewsletterMailService service =
        new NewsletterMailService(dispatcher, new AppConfig(), msg, userService);
    private final User subscriber = new User("reader@example.com", "Ala", "Kot", "", "ala");

    @Test
    void shouldPutAnUploadedThumbnailInTheMail() {
        // An uploaded thumbnail is the common case — the admin panel uploads, it rarely links out.
        News news = new News("Nowy sezon");
        news.setThumbnailFilename("cover.jpg");

        assertTrue(bodyOf(news).contains("<img src=\"" + BASE + "/api/files/news/cover.jpg\""),
            "an uploaded thumbnail must reach the subscriber");
    }

    @Test
    void shouldPreferAnExternalThumbnailOverAnUploadedOne() {
        News news = new News("Nowy sezon");
        news.setThumbnailUrl("https://cdn.example/cover.jpg");
        news.setThumbnailFilename("cover.jpg");

        String body = bodyOf(news);
        assertTrue(body.contains("<img src=\"https://cdn.example/cover.jpg\""));
        assertFalse(body.contains("/api/files/news/cover.jpg"));
    }

    @Test
    void shouldCropAroundTheFocalPointTheAdminSet() {
        News news = new News("Nowy sezon");
        news.setThumbnailFilename("cover.jpg");
        news.setThumbnailFocalPointX(0.25f);
        news.setThumbnailFocalPointY(0.8f);

        assertTrue(bodyOf(news).contains("object-position: 25% 80%;"));
    }

    @Test
    void shouldCentreTheCropWithoutAFocalPoint() {
        News news = new News("Nowy sezon");
        news.setThumbnailFilename("cover.jpg");

        assertTrue(bodyOf(news).contains("object-position: 50% 50%;"));
    }

    @Test
    void shouldLeaveTheThumbnailOutWhenThereIsNone() {
        assertFalse(bodyOf(new News("Nowy sezon")).contains("max-height: 340px"));
    }

    @Test
    void shouldRenderTheArticleFormattingLikeTheSiteDoes() {
        // The site renders these through renderRichText; the mail used to paste them raw, so
        // subscribers read "## Co zabieramy" and "**zimowy cykl**".
        News news = new News("Nowy sezon");
        NewsContentBlock text = new NewsContentBlock(news, BlockType.TEXT);
        text.setContent("Zapraszamy na **zimowy cykl**\n## Co zabieramy\n- kask <b>&</b>");

        String body = bodyOf(news, List.of(text));

        assertTrue(body.contains("<strong>zimowy cykl</strong>"));
        assertTrue(body.contains("<h3 style=\"margin:16px 0 4px 0;font-size:16px;color:#f3f4f6;\">Co zabieramy</h3>"),
            "the heading must be light — the newsletter card is dark");
        assertTrue(body.contains("<li>kask &lt;b&gt;&amp;&lt;/b&gt;</li>"), "raw HTML is escaped, as on the site");
        assertFalse(body.contains("**") || body.contains("## "));
    }

    @Test
    void shouldDrawAGalleryBlockAsARowOfPhotosWithAButtonToTheArticle() {
        News news = new News("Obóz");
        UUID albumId = UUID.randomUUID();
        NewsContentBlock block = new NewsContentBlock(news, BlockType.GALLERY);
        block.setAlbumId(albumId);
        block.setCaption("Dzień <drugi>");
        when(msg.getForLang(eq("email.newsletter.gallery.more"), any(), eq(32))).thenReturn("Zobacz wszystkie zdjęcia (32)");

        String body = bodyOf(news, List.of(block), Map.of(albumId, new NewsletterGallery(
            "Frankenjura & co", List.of(BASE + "/api/files/gallery/a.jpg", BASE + "/api/files/gallery/b.jpg"), 32)));

        assertTrue(body.contains("<img src=\"" + BASE + "/api/files/gallery/a.jpg\""));
        assertTrue(body.contains("<img src=\"" + BASE + "/api/files/gallery/b.jpg\""));
        assertTrue(body.contains("Frankenjura &amp; co"), "the album name is escaped");
        assertTrue(body.contains("Dzień &lt;drugi&gt;"), "the caption is escaped");
        assertTrue(body.contains(">Zobacz wszystkie zdjęcia (32)</a>"),
            "the button must be a text link, or the plain-text part loses it");
    }

    @Test
    void shouldLinkToTheArticleRouteTheSiteActuallyHas() {
        // The site serves articles at /aktualnosci/{id}; /news/{id} has no route and lands on the
        // 404 page — which is where every newsletter title link went until 2026-11-22.
        News news = new News("Nowy sezon");
        java.util.UUID id = UUID.randomUUID();
        setId(news, id);

        String body = bodyOf(news);

        assertTrue(body.contains("href=\"" + BASE + "/aktualnosci/" + id + "\""));
        assertFalse(body.contains("/news/" + id));
    }

    private static void setId(News news, UUID id) {
        try {
            var field = News.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(news, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shouldSkipAGalleryBlockWhoseAlbumIsNotPublished() {
        // The caller leaves unpublished, deleted and empty albums out of the map.
        News news = new News("Obóz");
        NewsContentBlock block = new NewsContentBlock(news, BlockType.GALLERY);
        block.setAlbumId(UUID.randomUUID());

        String body = bodyOf(news, List.of(block), Map.of());

        assertFalse(body.contains("/api/files/gallery/"));
        assertFalse(body.contains("<table role=\"presentation\""));
    }

    private String bodyOf(News news) {
        return bodyOf(news, List.of());
    }

    private String bodyOf(News news, List<NewsContentBlock> blocks) {
        return bodyOf(news, blocks, Map.of());
    }

    private String bodyOf(News news, List<NewsContentBlock> blocks, Map<UUID, NewsletterGallery> galleries) {
        when(userService.newsletterUnsubscribeToken(subscriber)).thenReturn("token");
        service.sendToAll(news, blocks, galleries, List.of(subscriber), BASE);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendNewsletterHtml(eq("reader@example.com"), anyString(), body.capture(), any());
        return body.getValue();
    }
}
