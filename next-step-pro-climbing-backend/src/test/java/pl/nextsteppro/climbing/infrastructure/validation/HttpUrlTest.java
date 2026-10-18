package pl.nextsteppro.climbing.infrastructure.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://nextsteppro.pl/api/files/news/a.jpg",
        "http://example.com",
        "HTTPS://Example.com/Path?q=1#x",
    })
    void shouldAcceptAbsoluteHttpUrls(String url) {
        assertTrue(HttpUrl.Validator.isHttpUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "JavaScript:alert(1)",
        "data:text/html;base64,PHNjcmlwdD4=",
        "//evil.example/x.jpg",
        "/relative/path",
        "https://x.pl/a.jpg\"><meta http-equiv=\"refresh\">",
        "https://x.pl/a b.jpg",
        "https:///nohost",
        "ftp://example.com/file",
        "not a url",
    })
    void shouldRefuseAnythingElse(String url) {
        assertFalse(HttpUrl.Validator.isHttpUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/kursy", "/kalendarz?view=week", "/aktualnosci/abc#top"})
    void shouldAcceptASitePathWhereAllowed(String path) {
        assertTrue(HttpUrl.Validator.isSitePath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//evil.example", "/\\evil.example", "kursy", "/a\"b", "/ spaced", "javascript:alert(1)"})
    void shouldRefuseWhatOnlyLooksLikeASitePath(String path) {
        // `//host` and `/\host` are another site wearing a path's clothes — browsers follow both
        assertFalse(HttpUrl.Validator.isSitePath(path));
    }
}
