package com.finny.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI. Em produção fica desligada (API_DOCS_ENABLED=false);
 * a especificação versionada fica em docs/openapi.json.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finnyOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Fini API")
                .version("2.0.0")
                .description("""
                    API do Fini, assistente financeiro com IA.

                    Autenticação: envie `Authorization: Bearer <accessToken>`. O access token dura 30 minutos; \
                    renove com POST /api/v1/auth/refresh (o refresh token é de uso único e é trocado a cada renovação).

                    Erros seguem sempre o formato `{timestamp, status, error, message, errors}`. \
                    Respostas 429 trazem o header `Retry-After` (segundos).

                    Chat: a IA nunca grava dados sozinha. Ela devolve `proposedActions`, que o app mostra como \
                    cartões de confirmação; ao confirmar, o app chama o endpoint REST correspondente."""))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Access token obtido em /api/v1/auth/login")));
    }
}
