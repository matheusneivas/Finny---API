package com.finny.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finnyOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Finny API")
                .description("API REST para o Finny - Assistente Financeiro Pessoal com IA")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Equipe Finny")
                    .email("contato@finny.app")
                    .url("https://finny.app"))
                .license(new License()
                    .name("Proprietário")
                    .url("https://finny.app/terms")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Ambiente de Desenvolvimento"),
                new Server()
                    .url("https://api.finny.app")
                    .description("Ambiente de Produção")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT obtido no endpoint /api/v1/auth/login")));
    }
}
