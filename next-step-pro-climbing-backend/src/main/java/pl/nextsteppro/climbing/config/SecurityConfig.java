package pl.nextsteppro.climbing.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticationFilter;

import java.time.Instant;
import java.util.Locale;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppConfig appConfig;
    private final Environment environment;
    private final OAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final MessageSource messageSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AppConfig appConfig, Environment environment,
                          OAuth2UserService oAuth2UserService, OAuth2SuccessHandler oAuth2SuccessHandler,
                          MessageSource messageSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appConfig = appConfig;
        this.environment = environment;
        this.oAuth2UserService = oAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.messageSource = messageSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF protection is disabled for stateless JWT-based API.
            // Rationale:
            // - JWT tokens are in Authorization header (not cookies), so browsers don't send them automatically
            // - CSRF attacks rely on automatic credential transmission (cookies)
            // - Same-Origin Policy prevents cross-origin access to localStorage
            // Security measures in place:
            // - XSS prevention via React auto-escaping + CSP headers (nginx.conf)
            // - Input sanitization and validation
            // - Rate limiting on auth endpoints
            // - Proper CORS configuration
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Swagger/OpenAPI documentation (only accessible in dev profile)
                if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll();
                }
                // Health check endpoint (used by Docker healthcheck and deploy verification)
                auth.requestMatchers("/actuator/health").permitAll();
                // Authentication endpoints
                auth.requestMatchers("/api/auth/**").permitAll()
                    // OAuth2 endpoints
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    // Public calendar endpoints
                    .requestMatchers(HttpMethod.GET, "/api/calendar/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                    // Public instructor, gallery and news endpoints
                    .requestMatchers(HttpMethod.GET, "/api/instructors/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/gallery/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/news/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/courses/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/videos/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                    // Public site settings (hero image etc.)
                    .requestMatchers(HttpMethod.GET, "/api/settings/**").permitAll()
                    // OG meta tags for social sharing bots (GET + HEAD - bots use HEAD to preflight)
                    .requestMatchers("/api/og/**").permitAll()
                    // Public unsubscribe endpoint (no login required - GDPR compliance)
                    .requestMatchers(HttpMethod.GET, "/api/user/unsubscribe").permitAll();
                // Dev endpoints only in dev profile
                if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
                    auth.requestMatchers("/api/dev/**").permitAll();
                }
                // Admin endpoints require ADMIN role
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // User-specific endpoints require authentication
                    .requestMatchers("/api/user/**").authenticated()
                    .requestMatchers("/api/reservations/**").authenticated()
                    // Default: require authentication
                    .anyRequest().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    Locale locale = resolveLocaleFromRequest(request);
                    String message = messageSource.getMessage("error.unauthorized", null, locale);
                    writeJsonError(response, 401, "UNAUTHORIZED", message);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    Locale locale = resolveLocaleFromRequest(request);
                    String message = messageSource.getMessage("error.forbidden", null, locale);
                    writeJsonError(response, 403, "FORBIDDEN", message);
                })
            )
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var configuration = new org.springframework.web.cors.CorsConfiguration();
        var origins = java.util.Arrays.stream(appConfig.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .toList();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        // OG endpoints are called by social media bots (FB, WhatsApp) which send their own Origin.
        // These endpoints are public and read-only, so allow all origins without credentials.
        var ogConfiguration = new org.springframework.web.cors.CorsConfiguration();
        ogConfiguration.setAllowedOriginPatterns(java.util.List.of("*"));
        ogConfiguration.setAllowedMethods(java.util.List.of("GET", "HEAD"));
        ogConfiguration.setAllowedHeaders(java.util.List.of("*"));
        ogConfiguration.setAllowCredentials(false);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/og/**", ogConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write(
            "{\"code\":\"" + code + "\",\"message\":\"" + escaped + "\",\"timestamp\":\"" + Instant.now() + "\"}"
        );
    }

    private static Locale resolveLocaleFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isEmpty()) {
            String lang = header.split("[,;_-]")[0].trim().toLowerCase();
            if ("en".equals(lang)) return Locale.of("en");
            if ("es".equals(lang)) return Locale.of("es");
        }
        return Locale.of("pl");
    }
}
