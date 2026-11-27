package com.finny.api.application.service;

import com.finny.api.application.exception.TooManyRequestsException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.domain.entity.EmailCode;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.EmailCodePurpose;
import com.finny.api.domain.repository.EmailCodeRepository;
import com.finny.api.infrastructure.mail.MailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Códigos de 6 dígitos por e-mail (verificação de e-mail e recuperação de senha).
 * <ul>
 *   <li>Só o HMAC-SHA256 do código vai para o banco; o código nunca é logado.</li>
 *   <li>Vale 15 minutos, aceita 5 tentativas e só o código mais recente vale.</li>
 *   <li>Reenvio com intervalo mínimo de 60 s e no máximo 10 códigos por dia (custo e abuso).</li>
 *   <li>O e-mail sai depois do commit, para não mandar código que não ficou gravado.</li>
 * </ul>
 */
@Service
@Slf4j
public class EmailCodeService {

    public static final Duration TTL = Duration.ofMinutes(15);
    public static final Duration COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;
    public static final int MAX_PER_DAY = 10;
    static final String INVALID = "Código inválido ou expirado. Confira ou peça um novo.";

    private final EmailCodeRepository repository;
    private final ApplicationEventPublisher events;
    private final MailSender mailSender;
    private final Clock clock;
    private final byte[] hmacKey;
    private final SecureRandom random = new SecureRandom();
    /** Envio fora da thread da requisição: a resposta não demora mais quando a conta existe. */
    private final java.util.concurrent.Executor sender;

    public EmailCodeService(EmailCodeRepository repository, ApplicationEventPublisher events, MailSender mailSender,
                            Clock clock, @Value("${jwt.secret}") String jwtSecret,
                            @Value("${app.mail.async:true}") boolean async) {
        this.sender = async ? java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "mail-sender");
            t.setDaemon(true);
            return t;
        }) : Runnable::run;
        this.repository = repository;
        this.events = events;
        this.mailSender = mailSender;
        this.clock = clock;
        this.hmacKey = hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "fini-email-codes-v1");
    }

    /** Evento interno: o código só existe em memória até o e-mail sair. */
    public record CodeIssued(String email, String name, EmailCodePurpose purpose, String code) {}

    @Transactional(propagation = Propagation.REQUIRED)
    public void issue(User user, EmailCodePurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose).ifPresent(last -> {
            long wait = Duration.between(now, last.getCreatedAt().plus(COOLDOWN)).toSeconds();
            if (wait > 0) {
                throw new TooManyRequestsException("Aguarde " + wait + " segundos para pedir outro código.", wait);
            }
        });
        if (repository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now.minusDays(1)) >= MAX_PER_DAY) {
            throw new TooManyRequestsException("Você pediu muitos códigos hoje. Tente novamente mais tarde.", 3600);
        }

        repository.consumeOpen(user.getId(), purpose, now);
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailCode row = new EmailCode();
        row.setId(UUID.randomUUID());
        row.setUserId(user.getId());
        row.setPurpose(purpose);
        row.setCodeHash(hash(user.getId(), purpose, code));
        row.setAttempts(0);
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(TTL));
        repository.save(row);

        events.publishEvent(new CodeIssued(user.getEmail(), user.getFullName(), purpose, code));
        log.info("Código por e-mail emitido | user: {} | tipo: {}", user.getId(), purpose);
    }

    /** true se existe um código ainda válido (não vencido, não usado, com tentativas). */
    @Transactional(readOnly = true)
    public boolean hasActiveCode(UUID userId, EmailCodePurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
            .filter(c -> c.getConsumedAt() == null && c.getExpiresAt().isAfter(now) && c.getAttempts() < MAX_ATTEMPTS)
            .isPresent();
    }

    /**
     * Confere o código. Tentativa errada conta mesmo com a exceção (o chamador usa noRollbackFor).
     * Código certo é consumido e não serve de novo.
     */
    @Transactional(propagation = Propagation.REQUIRED, noRollbackFor = ValidationException.class)
    public void consume(UUID userId, EmailCodePurpose purpose, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new ValidationException(INVALID);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        EmailCode row = repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
            .filter(c -> c.getConsumedAt() == null && c.getExpiresAt().isAfter(now))
            .orElseThrow(() -> new ValidationException(INVALID));
        if (row.getAttempts() >= MAX_ATTEMPTS) {
            throw new ValidationException("Muitas tentativas com este código. Peça um novo.");
        }
        boolean match = MessageDigest.isEqual(
            row.getCodeHash().getBytes(StandardCharsets.US_ASCII),
            hash(userId, purpose, code).getBytes(StandardCharsets.US_ASCII));
        if (!match) {
            row.setAttempts(row.getAttempts() + 1);
            repository.save(row);
            int left = MAX_ATTEMPTS - row.getAttempts();
            throw new ValidationException(left > 0
                ? "Código incorreto. Você ainda tem " + left + (left == 1 ? " tentativa." : " tentativas.")
                : "Muitas tentativas com este código. Peça um novo.");
        }
        row.setConsumedAt(now);
        repository.save(row);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendAfterCommit(CodeIssued event) {
        sender.execute(() -> {
            try {
                EmailTemplates.Message m = EmailTemplates.forCode(event.purpose(), event.code(), event.name());
                mailSender.send(event.email(), m.subject(), m.text(), m.html());
            } catch (RuntimeException e) {
                // A pessoa pode pedir reenvio; não derrubamos o cadastro por falha do provedor.
                log.error("E-mail de código não enviado | tipo: {} | erro: {}", event.purpose(), e.getMessage());
            }
        });
    }

    /** Limpeza diária de códigos antigos. */
    @Scheduled(cron = "0 50 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeOld() {
        int removed = repository.deleteCreatedBefore(OffsetDateTime.now(clock).minusDays(7));
        if (removed > 0) log.info("Códigos de e-mail antigos removidos: {}", removed);
    }

    private String hash(UUID userId, EmailCodePurpose purpose, String code) {
        return HexFormat.of().formatHex(hmac(hmacKey, userId + ":" + purpose + ":" + code));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
