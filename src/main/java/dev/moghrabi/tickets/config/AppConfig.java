package dev.moghrabi.tickets.config;

import java.time.Clock;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Swagger-UI bekommt einen "Authorize"-Knopf für HTTP Basic. */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketsystem")
                        .description("Support-Tickets mit Rollen, Statusworkflow, SLA, Kommentaren und Benachrichtigungen. "
                                + "Demo-Benutzer: anna, dora (REPORTER), ben, clara (AGENT), admin (ADMIN), Passwort jeweils demo123.")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes("basic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basic"));
    }
}
