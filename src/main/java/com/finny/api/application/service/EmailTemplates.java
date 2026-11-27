package com.finny.api.application.service;

import com.finny.api.domain.enums.EmailCodePurpose;
import org.springframework.web.util.HtmlUtils;

/** Textos dos e-mails. O código vem no começo do assunto (o iPhone sugere ele no teclado). */
final class EmailTemplates {

    record Message(String subject, String text, String html) {}

    private EmailTemplates() {}

    static Message forCode(EmailCodePurpose purpose, String code, String name) {
        String first = firstName(name);
        boolean reset = purpose == EmailCodePurpose.RESET_PASSWORD;
        String subject = code + " é seu código do Finny";
        String intro = reset
            ? "Recebemos um pedido para criar uma nova senha na sua conta do Finny. Use o código abaixo no app:"
            : "Falta pouco! Use o código abaixo no app do Finny para confirmar seu e-mail:";
        String outro = reset
            ? "Se não foi você, ignore este e-mail. Sua senha continua a mesma."
            : "Se você não criou uma conta no Finny, ignore este e-mail.";
        String hello = first.isEmpty() ? "Olá!" : "Olá, " + first + "!";

        String text = hello + "\n\n" + intro + "\n\n" + code + "\n\nO código vale por 15 minutos.\n\n" + outro + "\n\nFini";
        String html = """
            <!doctype html><html lang="pt-BR"><body style="margin:0;padding:24px;background:#f4f6f5;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#13201a">
            <table role="presentation" width="100%%" style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px">
            <tr><td>
            <p style="margin:0 0 8px;font-size:20px;font-weight:700;color:#16a06b">Finny</p>
            <p style="margin:0 0 16px;font-size:16px">%s</p>
            <p style="margin:0 0 24px;font-size:15px;line-height:1.5">%s</p>
            <p style="margin:0 0 24px;font-size:34px;font-weight:700;letter-spacing:8px;text-align:center;background:#eef8f3;border-radius:12px;padding:16px">%s</p>
            <p style="margin:0 0 8px;font-size:14px;color:#4b5a53">O código vale por 15 minutos.</p>
            <p style="margin:0;font-size:14px;color:#4b5a53">%s</p>
            </td></tr></table></body></html>
            """.formatted(HtmlUtils.htmlEscape(hello), intro, code, outro);
        return new Message(subject, text, html);
    }

    private static String firstName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        String first = space > 0 ? trimmed.substring(0, space) : trimmed;
        return first.length() > 40 ? first.substring(0, 40) : first;
    }
}
