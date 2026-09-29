package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.api.user.UserService;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.news.BlockType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;
import pl.nextsteppro.climbing.infrastructure.storage.FileUrls;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class NewsletterMailService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterMailService.class);

    private final MailDispatcher mailDispatcher;
    private final MessageService msg;
    private final UserService userService;
    private final String siteUrl;

    public NewsletterMailService(MailDispatcher mailDispatcher, AppConfig appConfig, MessageService msg, UserService userService) {
        this.mailDispatcher = mailDispatcher;
        this.msg = msg;
        this.userService = userService;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async("mailCampaignExecutor")
    public void sendToAll(News news, List<NewsContentBlock> blocks, List<User> subscribers, String baseUrl) {
        log.info("Sending newsletter '{}' to {} subscribers", news.getTitle(), subscribers.size());
        for (User subscriber : subscribers) {
            String unsubscribeToken = userService.newsletterUnsubscribeToken(subscriber);
            String unsubscribeUrl = baseUrl + "/api/user/unsubscribe?token=" + unsubscribeToken;
            sendToUser(news, blocks, subscriber, baseUrl, unsubscribeUrl);
        }
        log.info("Newsletter '{}' sent", news.getTitle());
    }

    private void sendToUser(News news, List<NewsContentBlock> blocks, User subscriber, String baseUrl, String unsubscribeUrl) {
        String lang = subscriber.getPreferredLanguage();
        String subject = news.getTitle();
        String body = buildBody(news, blocks, subscriber, baseUrl, lang, unsubscribeUrl);

        mailDispatcher.sendNewsletterHtml(subscriber.getEmail(), subject, body, unsubscribeUrl);
    }

    private String buildBody(News news, List<NewsContentBlock> blocks, User subscriber, String baseUrl, String lang, String unsubscribeUrl) {
        String settingsUrl = baseUrl + "/settings";
        String newsUrl = baseUrl + "/news/" + news.getId();
        String thumbnailHtml = buildThumbnailHtml(news, baseUrl);
        String blocksHtml = buildBlocksHtml(blocks, baseUrl);

        String footerText = msg.getForLang("email.newsletter.footer", lang, unsubscribeUrl, settingsUrl);

        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px; margin: 0;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <a href="%s" style="display: inline-block; text-decoration: none; cursor: pointer; line-height: 0; font-size: 0;"><img src="cid:logo" alt="Next Step Pro Climbing" style="height: 80px; display: block;" /></a>
                    </div>
                    <div style="padding: 20px 30px 30px;">
                        <h1 style="color: #3b82f6; margin-top: 16px; margin-bottom: 20px; font-size: 24px; line-height: 1.3;">
                            <a href="%s" style="color: #3b82f6; text-decoration: none;">%s</a>
                        </h1>
                        %s
                        %s
                        <hr style="border: none; border-top: 1px solid #2d2d44; margin: 30px 0;">
                        <p style="font-size: 12px; color: #6b7280; text-align: center; line-height: 1.6;">
                            %s<br>%s
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                siteUrl,
                newsUrl,
                escapeHtml(news.getTitle()),
                thumbnailHtml,
                blocksHtml,
                footerText,
                msg.getForLang("email.footer.slogan", lang)
        );
    }

    /**
     * The article's cover, uploaded or external — the same rule the site uses. Only the external
     * one used to get through, so the usual case (uploaded in the panel) sent the mail without it.
     *
     * <p>Cropped around the focal point the admin set, like the site does. Clients that honour
     * {@code object-fit} honour {@code object-position} too; the rest ignore both.
     */
    private String buildThumbnailHtml(News news, String baseUrl) {
        String url = FileUrls.preferExternal(news.getThumbnailUrl(), baseUrl, "news", news.getThumbnailFilename());
        if (url == null) return "";
        return """
            <div style="margin-bottom: 20px; border-radius: 8px; overflow: hidden;">
                <img src="%s" alt="" style="width: 100%%; display: block; max-height: 340px; object-fit: cover; object-position: %s;" />
            </div>
            """.formatted(escapeHtml(url), focalPosition(news));
    }

    private static String focalPosition(News news) {
        Float x = news.getThumbnailFocalPointX();
        Float y = news.getThumbnailFocalPointY();
        if (x == null || y == null) return "50% 50%";
        // Locale.ROOT is habit, not load-bearing: %.0f prints no decimal separator in any locale.
        return String.format(Locale.ROOT, "%.0f%% %.0f%%", x * 100, y * 100);
    }

    private String buildBlocksHtml(List<NewsContentBlock> blocks, String baseUrl) {
        if (blocks.isEmpty()) return "";

        var sb = new StringBuilder();
        for (NewsContentBlock block : blocks) {
            if (block.getBlockType() == BlockType.TEXT && block.getContent() != null) {
                sb.append("""
                    <div style="font-size: 16px; line-height: 1.7; color: #d1d5db; margin-bottom: 18px;">
                        %s
                    </div>
                    """.formatted(MailRichText.toHtml(block.getContent(), "#f3f4f6")));
            } else if (block.getBlockType() == BlockType.IMAGE) {
                String imgUrl = FileUrls.preferExternal(block.getImageUrl(), baseUrl, "news", block.getImageFilename());
                if (imgUrl != null) {
                    sb.append("""
                        <div style="margin: 20px 0; border-radius: 8px; overflow: hidden;">
                            <img src="%s" alt="" style="width: 100%%; display: block;" />
                            %s
                        </div>
                        """.formatted(imgUrl, buildCaptionHtml(block.getCaption())));
                }
            }
        }
        return sb.toString();
    }

    private String buildCaptionHtml(@Nullable String caption) {
        if (caption == null || caption.isBlank()) return "";
        return "<p style=\"font-size: 13px; color: #9ca3af; margin: 8px 0 0; font-style: italic;\">%s</p>"
                .formatted(escapeHtml(caption));
    }

    private static String escapeHtml(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }
}
