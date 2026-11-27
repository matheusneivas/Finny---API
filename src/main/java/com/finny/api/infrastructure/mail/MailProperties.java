package com.finny.api.infrastructure.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Envio de e-mails (prefixo app.mail.*). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** resend, brevo, log (só desenvolvimento: escreve o código no log) ou none (não envia). */
    private String provider = "none";

    /** Remetente. Ex.: "Fini <codigo@fini.com.br>". */
    private String from = "Fini <onboarding@resend.dev>";

    /** Chave do provedor (RESEND_API_KEY ou BREVO_API_KEY). */
    private String apiKey = "";

    public boolean enabled() {
        return !"none".equalsIgnoreCase(provider);
    }
}
