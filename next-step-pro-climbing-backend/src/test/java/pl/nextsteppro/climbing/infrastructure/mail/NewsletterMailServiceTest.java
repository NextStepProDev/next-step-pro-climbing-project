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
    private final NewsletterMailService service =
        new NewsletterMailService(dispatcher, new AppConfig(), mock(MessageService.class), userService);
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

    private String bodyOf(News news) {
        return bodyOf(news, List.of());
    }

    private String bodyOf(News news, List<NewsContentBlock> blocks) {
        when(userService.newsletterUnsubscribeToken(subscriber)).thenReturn("token");
        service.sendToAll(news, blocks, List.of(subscriber), BASE);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendNewsletterHtml(eq("reader@example.com"), anyString(), body.capture(), any());
        return body.getValue();
    }
}
