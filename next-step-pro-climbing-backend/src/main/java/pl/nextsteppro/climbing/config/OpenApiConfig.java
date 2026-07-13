package pl.nextsteppro.climbing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.version:dev}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Next Step Pro Climbing API")
                .version(appVersion)
                .description("""
                    Booking system for the Next Step Pro Climbing school.

                    ## Features
                    - **Calendar** - browsing available slots
                    - **Reservations** - signing up for classes
                    - **Waitlist** - queue when no seats are left
                    - **Admin panel** - managing slots and events

                    ## Authentication
                    The API uses OAuth2 (Google/Apple) for user authentication.
                    Calendar endpoints are public, the rest require login.
                    """)
                .contact(new Contact()
                    .name("Next Step Pro Climbing")
                    .email("admin@nextsteppro.pl")
                    .url("https://nextsteppro.pl"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://nextsteppro.pl/terms")))
            .servers(List.of(
                new Server()
                    .url(baseUrl)
                    .description("Current Server"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development")))
            .tags(List.of(
                new Tag().name("Calendar").description("Public calendar with available slots"),
                new Tag().name("Reservations").description("User reservation management"),
                new Tag().name("User").description("User profile"),
                new Tag().name("Admin - Slots").description("Slot management (admin only)"),
                new Tag().name("Admin - Events").description("Event management (admin only)"),
                new Tag().name("Admin - Users").description("User management (admin only)")))
            .components(new Components()
                .addSecuritySchemes("cookieAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("Session cookie after OAuth2 login")))
            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
    }
}
