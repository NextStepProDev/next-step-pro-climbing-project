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
                    System rezerwacji zajęć w szkole wspinaczkowej Next Step Pro Climbing.

                    ## Funkcjonalności
                    - **Kalendarz** - przeglądanie dostępnych terminów
                    - **Rezerwacje** - zapisywanie się na zajęcia
                    - **Waitlist** - lista rezerwowa gdy brak miejsc
                    - **Panel admina** - zarządzanie terminami i wydarzeniami

                    ## Autoryzacja
                    API używa OAuth2 (Google/Apple) do autoryzacji użytkowników.
                    Endpointy kalendarza są publiczne, pozostałe wymagają zalogowania.
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
                new Tag().name("Calendar").description("Publiczny kalendarz z dostępnymi terminami"),
                new Tag().name("Reservations").description("Zarządzanie rezerwacjami użytkownika"),
                new Tag().name("User").description("Profil użytkownika"),
                new Tag().name("Admin - Slots").description("Zarządzanie terminami (tylko admin)"),
                new Tag().name("Admin - Events").description("Zarządzanie wydarzeniami (tylko admin)"),
                new Tag().name("Admin - Users").description("Zarządzanie użytkownikami (tylko admin)")))
            .components(new Components()
                .addSecuritySchemes("cookieAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("Session cookie after OAuth2 login")))
            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
    }
}
