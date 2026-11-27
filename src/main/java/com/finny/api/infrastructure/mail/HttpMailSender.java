package com.finny.api.infrastructure.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resend (api.resend.com) ou Brevo (api.brevo.com) via HTTPS. */
@Slf4j
public class HttpMailSender implements MailSender {

    private static final Pattern NAMED = Pattern.compile("^\\s*(.*?)\\s*<([^>]+)>\\s*$");

    private final MailProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public HttpMailSender(MailProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        boolean brevo = "brevo".equalsIgnoreCase(properties.getProvider());
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
            Object body;
            if (brevo) {
                Matcher m = NAMED.matcher(properties.getFrom());
                Map<String, String> sender = m.matches()
                    ? Map.of("name", m.group(1), "email", m.group(2))
                    : Map.of("email", properties.getFrom().trim());
                body = Map.of("sender", sender, "to", List.of(Map.of("email", to)),
                    "subject", subject, "textContent", text, "htmlContent", html);
                request.uri(URI.create("https://api.brevo.com/v3/smtp/email")).header("api-key", properties.getApiKey());
            } else {
                body = Map.of("from", properties.getFrom(), "to", List.of(to), "subject", subject, "text", text, "html", html);
                request.uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + properties.getApiKey());
            }
            HttpResponse<String> response = http.send(
                request.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // O corpo da resposta de erro não tem o código enviado; ajuda a diagnosticar domínio/chave.
                String detail = response.body() == null ? "" : response.body();
                log.error("Falha ao enviar e-mail | provedor: {} | status: {} | resposta: {}", properties.getProvider(),
                    response.statusCode(), detail.length() > 300 ? detail.substring(0, 300) : detail);
                throw new IllegalStateException("Provedor de e-mail recusou o envio (" + response.statusCode() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha de rede ao enviar e-mail", e);
        }
    }
}
