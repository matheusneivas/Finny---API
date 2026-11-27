package com.finny.api.infrastructure.mail;

/** Envia um e-mail transacional. Implementações não devem logar o conteúdo (tem código de acesso). */
public interface MailSender {

    void send(String to, String subject, String text, String html);
}
