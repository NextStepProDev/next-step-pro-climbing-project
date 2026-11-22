package pl.nextsteppro.climbing.infrastructure.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MailRichTextTest {

    private static final String HEADING = "#000";

    @Test
    void shouldKeepTheNumbersWhenANumberedListDoesNotStartAtOne() {
        // Given a list resumed after a heading
        String text = "## Część druga\n3. seria\n4. seria";

        // When
        String html = MailRichText.toHtml(text, HEADING);

        // Then
        assertThat(html).contains("<ol start=\"3\"");
    }

    @Test
    void shouldLeaveAListThatStartsAtOneWithoutAStartAttribute() {
        assertThat(MailRichText.toHtml("1. seria\n2. seria", HEADING)).doesNotContain("start=");
    }

    @Test
    void shouldCarryTheStartToLetteredLists() {
        assertThat(MailRichText.toHtml("c) wariant", HEADING)).contains("<ol type=\"a\" start=\"3\"");
    }

    @Test
    void shouldAcceptAnUppercaseLetteredListLikeTheSiteDoes() {
        // Given the site renders "C)" as a lettered list (its regex is case-insensitive)
        String html = MailRichText.toHtml("C) wariant\nD) drugi", HEADING);

        // Then the newsletter must not fall back to a plain paragraph
        assertThat(html).contains("<ol type=\"a\" start=\"3\"").contains("<li>wariant</li><li>drugi</li>");
    }

    @Test
    void shouldNotFailTheSendWhenTheTypedNumberIsOutOfRange() {
        // Given a number no int can hold
        String text = "99999999999. za dużo";

        // When / Then: falls back to the default numbering instead of throwing
        assertThatCode(() -> MailRichText.toHtml(text, HEADING)).doesNotThrowAnyException();
        assertThat(MailRichText.toHtml(text, HEADING)).doesNotContain("start=");
    }

    @Test
    void shouldIndentEveryListKindLikeTheSiteDoes() {
        assertThat(MailRichText.toHtml("• a", HEADING)).contains("padding-left:28px");
        assertThat(MailRichText.toHtml("1. a", HEADING)).contains("padding-left:28px");
        assertThat(MailRichText.toHtml("a) a", HEADING)).contains("padding-left:28px");
    }
}
