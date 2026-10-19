package pl.nextsteppro.climbing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.infrastructure.security.JwtService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every operation in the API spec, called with input it should refuse, never answers 5xx.
 *
 * <p>A 500 for a request the server understood and rightly rejected is the class CLAUDE.md names
 * under "Bzdura od klienta = 400, nie 500": each Spring exception describing a bad request needs
 * its own handler, or it falls into the catch-all and logs ERROR with a stack trace. Handlers were
 * added one type at a time, each after somebody happened to hit it; on 2026-10-19 a sweep of all
 * 338 operations found the next one (an upload route reached without a multipart body — eighteen
 * routes). This test is that sweep, run on every build, and it reads the spec from the running
 * application — so a controller added tomorrow is covered without anyone listing it.
 *
 * <p>⚠️ It shares the database with every other integration test and is not transactional (the
 * server runs on a real port, outside the test's transaction), so it must not change anything:
 * reads go with well-formed parameters, writes addressed by an id go to a random UUID (404 before
 * any change), writes with no id get only a body the JSON parser refuses, and id-less DELETEs are
 * skipped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // The spec is off by default (SWAGGER_ENABLED) and on only in dev; this test reads it, so it switches it on.
    properties = {"app.rate-limit.enabled=false", "springdoc.api-docs.enabled=true"})
@ActiveProfiles("test")
class ApiSweepIntegrationTest {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");
    private static final Set<String> SKIPPED_PREFIXES = Set.of("/api/dev", "/v3/", "/swagger");

    @MockitoBean JavaMailSender javaMailSender;
    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final List<User> created = new ArrayList<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BaseIntegrationTest.configureProperties(registry);
    }

    @BeforeEach
    void users() {
        created.add(user("sweep-admin@test.local", UserRole.ADMIN));
        created.add(user("sweep-client@test.local", UserRole.USER));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll(created);
    }

    @Test
    void shouldNeverAnswerAServerErrorToInputItShouldRefuse() throws Exception {
        // Outside the dev profile the spec sits behind a login.
        HttpResponse<String> docs = send("GET", "/v3/api-docs", null, jwtService.generateAccessToken(created.get(0)));
        assertEquals(200, docs.statusCode(), "No spec, no sweep: " + docs.body().substring(0, Math.min(300, docs.body().length())));
        JsonNode spec = json.readTree(docs.body());
        Map<String, String> callers = new LinkedHashMap<>();
        callers.put("anonymous", null);
        callers.put("client", jwtService.generateAccessToken(created.get(1)));
        callers.put("admin", jwtService.generateAccessToken(created.get(0)));

        List<String> serverErrors = new ArrayList<>();
        int calls = 0;
        for (Iterator<Map.Entry<String, JsonNode>> paths = spec.get("paths").fields(); paths.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = paths.next();
            String path = entry.getKey();
            if (SKIPPED_PREFIXES.stream().anyMatch(path::startsWith)) continue;
            boolean addressed = path.contains("{");
            for (Iterator<String> methods = entry.getValue().fieldNames(); methods.hasNext(); ) {
                String method = methods.next().toUpperCase();
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) continue;
                boolean write = !method.equals("GET");
                if (write && !addressed && method.equals("DELETE")) continue;

                List<String[]> variants = new ArrayList<>(); // {url, body}
                if (!write || addressed) {
                    variants.add(new String[]{fill(path, false), write && !method.equals("DELETE") ? "{}" : null});
                }
                if (addressed) {
                    variants.add(new String[]{fill(path, true), write && !method.equals("DELETE") ? "{}" : null});
                }
                if (write && !method.equals("DELETE")) {
                    variants.add(new String[]{fill(path, false), "{\"broken\":"});
                }
                for (Map.Entry<String, String> caller : callers.entrySet()) {
                    for (String[] variant : variants) {
                        calls++;
                        HttpResponse<String> response = send(method, variant[0], variant[1], caller.getValue());
                        if (response.statusCode() >= 500) {
                            serverErrors.add(response.statusCode() + " " + caller.getKey() + " " + method + " "
                                + variant[0] + " body=" + variant[1]);
                        }
                    }
                }
            }
        }
        assertTrue(calls > 1000, "The sweep must actually reach the API (made " + calls + " calls)");
        assertEquals(List.of(), serverErrors, "Refused input must be a 4xx — give the exception a handler");
    }

    private static String fill(String path, boolean nonsense) {
        Matcher matcher = PATH_VARIABLE.matcher(path);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase();
            String value;
            if (nonsense) value = "not-a-valid-value";
            else if (name.contains("yearmonth")) value = "2026-11";
            else if (name.equals("date") || name.endsWith("date")) value = "2026-11-05";
            else if (name.equals("month")) value = "2026-11-01";
            else if (name.equals("year")) value = "2026";
            else if (name.contains("targettype") || name.equals("target")) value = "slot";
            else if (name.contains("payertype")) value = "user";
            else if (name.equals("lang") || name.equals("language")) value = "pl";
            else if (name.equals("folder")) value = "gallery";
            else if (name.contains("filename")) value = UUID.randomUUID() + ".jpg";
            else value = UUID.randomUUID().toString();
            matcher.appendReplacement(out, value);
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private User user(String email, UserRole role) {
        User user = new User(email, "Sweep", "Test", "+48123123123", email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }
}
