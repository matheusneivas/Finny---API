package com.finny.api.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.domain.entity.IdempotencyRecord;
import com.finny.api.domain.repository.IdempotencyRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Idempotency-Key nas criações (transação, meta, aporte).
 * <p>
 * A criação e o registro da chave acontecem na mesma transação do banco. Se duas requisições com a
 * mesma chave chegarem juntas, a segunda esbarra na chave única, é desfeita por inteiro e devolve o
 * resultado da primeira. Sem cabeçalho, a rota funciona como antes.
 */
@Service
@Slf4j
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    static final Duration RETENTION = Duration.ofDays(45);
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final IdempotencyRecordRepository repository;
    private final ChatActionService chatActionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ChatActionService chatActionService,
                              ObjectMapper objectMapper, PlatformTransactionManager txManager, Clock clock) {
        this.repository = repository;
        this.chatActionService = chatActionService;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /**
     * Executa a criação uma única vez por (usuário, chave).
     *
     * @param scope identifica a operação (ex.: "POST /transactions"); a mesma chave em outra operação é recusada
     */
    public <T> ResponseEntity<T> execute(UUID userId, String key, String scope, HttpStatus status,
                                         Class<T> type, Supplier<T> action) {
        if (key == null) {
            return ResponseEntity.status(status).body(action.get());
        }
        String k = key.trim();
        if (!VALID_KEY.matcher(k).matches()) {
            throw new ValidationException("Idempotency-Key inválida (até 64 caracteres: letras, números, . _ : -)");
        }
        try {
            return tx.execute(s -> {
                Optional<IdempotencyRecord> existing = repository.findByUserIdAndKey(userId, k);
                if (existing.isPresent()) {
                    return replay(existing.get(), scope, type);
                }
                T body = action.get();
                IdempotencyRecord record = new IdempotencyRecord();
                record.setId(UUID.randomUUID());
                record.setUserId(userId);
                record.setKey(k);
                record.setScope(scope);
                record.setStatusCode(status.value());
                record.setResponseBody(write(body));
                record.setCreatedAt(OffsetDateTime.now(clock));
                repository.saveAndFlush(record);
                chatActionService.markConfirmedIfAction(userId, k);
                return ResponseEntity.status(status).body(body);
            });
        } catch (DataIntegrityViolationException race) {
            // Outra requisição com a mesma chave gravou primeiro; esta foi desfeita por inteiro.
            IdempotencyRecord winner = tx.execute(s -> repository.findByUserIdAndKey(userId, k).orElse(null));
            if (winner == null) throw race;
            return replay(winner, scope, type);
        }
    }

    private <T> ResponseEntity<T> replay(IdempotencyRecord record, String scope, Class<T> type) {
        if (!record.getScope().equals(scope)) {
            throw new BusinessException("Esta Idempotency-Key já foi usada em outra operação");
        }
        try {
            log.info("Requisição repetida com Idempotency-Key | user: {} | escopo: {}", record.getUserId(), scope);
            return ResponseEntity.status(record.getStatusCode())
                .header("Idempotent-Replayed", "true")
                .body(objectMapper.readValue(record.getResponseBody(), type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resposta guardada ilegível", e);
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao guardar a resposta", e);
        }
    }

    /** Limpeza diária das chaves antigas. */
    @Scheduled(cron = "0 45 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeOld() {
        int removed = repository.deleteCreatedBefore(OffsetDateTime.now(clock).minus(RETENTION));
        if (removed > 0) {
            log.info("Idempotency-Keys antigas removidas: {}", removed);
        }
    }
}
