package pl.nextsteppro.climbing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.nextsteppro.climbing.infrastructure.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppConfig appConfig;
    private final Environment environment;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AppConfig appConfig, Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appConfig = appConfig;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Swagger/OpenAPI documentation
                auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    // Authentication endpoints
                    .requestMatchers("/api/auth/**").permitAll()
                    // Public calendar endpoints
                    .requestMatchers(HttpMethod.GET, "/api/calendar/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll();
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
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
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
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
