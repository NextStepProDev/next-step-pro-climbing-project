package pl.nextsteppro.climbing.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.domain.news.BlockType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsContentBlock;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.List;

@Service
public class NewsletterMailService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;

    public NewsletterMailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    @Async
    public void sendToAll(News news, List<NewsContentBlock> blocks, List<User> subscribers, String baseUrl) {
        log.info("Sending newsletter '{}' to {} subscribers", news.getTitle(), subscribers.size());
        for (User subscriber : subscribers) {
            sendToUser(news, blocks, subscriber, baseUrl);
        }
        log.info("Newsletter '{}' sent", news.getTitle());
    }

    private void sendToUser(News news, List<NewsContentBlock> blocks, User subscriber, String baseUrl) {
        String lang = subscriber.getPreferredLanguage();
        String subject = news.getTitle();
        String body = buildBody(news, blocks, subscriber, baseUrl, lang);

        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(subscriber.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());

            var logoResource = new org.springframework.core.io.ClassPathResource("static/logo/logo-white.png");
            helper.addInline("logo", logoResource, "image/png");

            mailSender.send(message);
            log.debug("Newsletter sent to: {}", subscriber.getEmail());
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send newsletter to: {}", subscriber.getEmail(), e);
        }
    }

    private String buildBody(News news, List<NewsContentBlock> blocks, User subscriber, String baseUrl, String lang) {
        String settingsUrl = baseUrl + "/settings";
        String newsUrl = baseUrl + "/news/" + news.getId();
        String thumbnailHtml = buildThumbnailHtml(news);
        String blocksHtml = buildBlocksHtml(blocks, baseUrl);

        String unsubscribeText = msg.getForLang("email.newsletter.unsubscribe", lang);
        String footerText = msg.getForLang("email.newsletter.footer", lang, settingsUrl, unsubscribeText);

        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #0f0f1a; color: #e0e0e0; padding: 20px; margin: 0;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #1a1a2e; border-radius: 12px; overflow: hidden;">
                    <div style="text-align: center; padding: 24px 30px 0;">
                        <img src="cid:logo" alt="Next Step Pro Climbing" style="height: 60px;" />
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
                newsUrl,
                escapeHtml(news.getTitle()),
                thumbnailHtml,
                blocksHtml,
                footerText,
                msg.getForLang("email.footer.slogan", lang)
        );
    }

    private String buildThumbnailHtml(News news) {
        String url = resolveImageUrl(news.getThumbnailUrl(), news.getThumbnailFilename(), null);
        if (url == null) return "";
        return """
            <div style="margin-bottom: 20px; border-radius: 8px; overflow: hidden;">
                <img src="%s" alt="" style="width: 100%%; display: block; max-height: 340px; object-fit: cover;" />
            </div>
            """.formatted(url);
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
                    """.formatted(block.getContent()));
            } else if (block.getBlockType() == BlockType.IMAGE) {
                String imgUrl = resolveImageUrl(block.getImageUrl(), block.getImageFilename(), baseUrl);
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

    @Nullable
    private String resolveImageUrl(@Nullable String imageUrl, @Nullable String imageFilename, @Nullable String baseUrl) {
        if (imageUrl != null) return imageUrl;
        if (imageFilename != null && baseUrl != null) return baseUrl + "/api/files/news/" + imageFilename;
        return null;
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
