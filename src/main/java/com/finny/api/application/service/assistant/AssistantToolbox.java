package com.finny.api.application.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finny.api.application.dto.request.GoalContributionRequest;
import com.finny.api.application.dto.request.GoalRequest;
import com.finny.api.application.dto.request.TransactionRequest;
import com.finny.api.application.dto.response.ProposedActionResponse;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.Goal;
import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.enums.ExpenseFrequency;
import com.finny.api.domain.enums.GoalStatus;
import com.finny.api.domain.enums.ProposedActionType;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.domain.repository.GoalRepository;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.infrastructure.ai.AiToolDefinition;
import com.finny.api.infrastructure.ai.AiToolHandler;
import com.finny.api.infrastructure.ai.AiToolResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.finny.api.application.service.assistant.AssistantPromptBuilder.money;

/**
 * Ferramentas que a IA pode usar.
 * <ul>
 *   <li><b>Leitura</b> (search_transactions, list_goals): executadas no servidor, sempre
 *       restritas aos dados do próprio usuário.</li>
 *   <li><b>Escrita</b> (propose_*): NUNCA gravam nada. Validam os dados, resolvem categoria/ids
 *       e devolvem uma {@link ProposedActionResponse} para o app mostrar como cartão.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantToolbox {

    static final int MAX_PROPOSALS_PER_MESSAGE = 10;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ChatProperties chatProperties;
    private final Clock clock;

    // ── Definições ──────────────────────────────────────────────────────────

    public List<AiToolDefinition> definitions() {
        List<String> frequencies = List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");
        List<String> types = List.of("INCOME", "EXPENSE");

        return List.of(
            new AiToolDefinition(
                "search_transactions",
                "Busca lançamentos do usuário (somente leitura). Use para encontrar o id de um lançamento "
                    + "antes de editar/apagar, ou para responder perguntas sobre gastos de um período. "
                    + "Sem datas, busca os últimos 90 dias até hoje.",
                props(
                    "start_date", Map.of("type", "string", "description", "Data inicial YYYY-MM-DD"),
                    "end_date", Map.of("type", "string", "description", "Data final YYYY-MM-DD"),
                    "type", Map.of("type", "string", "enum", types),
                    "text", Map.of("type", "string", "description", "Trecho da descrição ou nome da categoria")),
                List.of()),
            new AiToolDefinition(
                "list_goals",
                "Lista as metas de economia do usuário com id, valores e status (somente leitura).",
                props(),
                List.of()),
            new AiToolDefinition(
                "propose_create_transaction",
                "Prepara um NOVO lançamento (despesa ou receita) para o usuário confirmar no app. Não salva nada.",
                props(
                    "description", Map.of("type", "string", "description", "Descrição curta, ex.: Mercado, Salário, PS5"),
                    "amount", Map.of("type", "number", "description", "Valor em reais (de cada parcela, se parcelado)"),
                    "type", Map.of("type", "string", "enum", types),
                    "category_name", Map.of("type", "string", "description", "Nome de uma categoria disponível"),
                    "transaction_date", Map.of("type", "string", "description", "Data YYYY-MM-DD (hoje se não informada)"),
                    "frequency", Map.of("type", "string", "enum", frequencies, "description", "ONCE para único; MONTHLY para parcelas mensais"),
                    "repeat_count", Map.of("type", "integer", "description", "Número de parcelas/repetições (1 = único)")),
                List.of("description", "amount", "type", "category_name")),
            new AiToolDefinition(
                "propose_update_transaction",
                "Prepara a EDIÇÃO de um lançamento existente para o usuário confirmar. Envie só os campos que mudam.",
                props(
                    "transaction_id", Map.of("type", "string", "description", "id do lançamento"),
                    "description", Map.of("type", "string"),
                    "amount", Map.of("type", "number"),
                    "type", Map.of("type", "string", "enum", types),
                    "category_name", Map.of("type", "string"),
                    "transaction_date", Map.of("type", "string", "description", "YYYY-MM-DD")),
                List.of("transaction_id")),
            new AiToolDefinition(
                "propose_delete_transaction",
                "Prepara a EXCLUSÃO de um lançamento para o usuário confirmar.",
                props("transaction_id", Map.of("type", "string", "description", "id do lançamento")),
                List.of("transaction_id")),
            new AiToolDefinition(
                "propose_create_goal",
                "Prepara uma NOVA meta de economia (ex.: carro, casa, viagem) para o usuário confirmar.",
                props(
                    "name", Map.of("type", "string", "description", "Nome da meta (até 50 caracteres)"),
                    "target_amount", Map.of("type", "number", "description", "Valor total a juntar, em reais"),
                    "deadline", Map.of("type", "string", "description", "Data limite YYYY-MM-DD, no futuro"),
                    "icon", Map.of("type", "string", "description", "Emoji opcional, ex.: 🚗")),
                List.of("name", "target_amount", "deadline")),
            new AiToolDefinition(
                "propose_goal_contribution",
                "Prepara um APORTE (dinheiro guardado) em uma meta ativa para o usuário confirmar.",
                props(
                    "goal_id", Map.of("type", "string", "description", "id da meta"),
                    "amount", Map.of("type", "number", "description", "Valor do aporte em reais")),
                List.of("goal_id", "amount"))
        );
    }

    /** Cria uma sessão de ferramentas para uma mensagem; acumula as propostas. */
    public Session newSession(UUID userId) {
        return new Session(userId);
    }

    public class Session implements AiToolHandler {

        private final UUID userId;
        private final List<ProposedActionResponse> proposals = new ArrayList<>();

        private Session(UUID userId) {
            this.userId = userId;
        }

        public List<ProposedActionResponse> proposals() {
            return Collections.unmodifiableList(proposals);
        }

        @Override
        public AiToolResult handle(String toolName, Map<String, Object> input) {
            log.info("Ferramenta da IA | user: {} | tool: {}", userId, toolName);
            try {
                return transactionTemplate.execute(status -> {
                    status.setRollbackOnly(); // ferramentas nunca gravam
                    return dispatch(toolName, input);
                });
            } catch (ToolInputException e) {
                return error(e.getMessage());
            } catch (Exception e) {
                log.warn("Erro inesperado na ferramenta {} | user: {}", toolName, userId, e);
                return error("Erro interno ao executar a ferramenta.");
            }
        }

        private AiToolResult dispatch(String toolName, Map<String, Object> input) {
            if (toolName.startsWith("propose_") && proposals.size() >= MAX_PROPOSALS_PER_MESSAGE) {
                throw new ToolInputException("Limite de " + MAX_PROPOSALS_PER_MESSAGE + " propostas por mensagem.");
            }
            return switch (toolName) {
                case "search_transactions" -> searchTransactions(input);
                case "list_goals" -> listGoals();
                case "propose_create_transaction" -> proposeCreateTransaction(input);
                case "propose_update_transaction" -> proposeUpdateTransaction(input);
                case "propose_delete_transaction" -> proposeDeleteTransaction(input);
                case "propose_create_goal" -> proposeCreateGoal(input);
                case "propose_goal_contribution" -> proposeContribution(input);
                default -> throw new ToolInputException("Ferramenta desconhecida: " + toolName);
            };
        }

        // ── Leitura ─────────────────────────────────────────────────────────

        private AiToolResult searchTransactions(Map<String, Object> in) {
            LocalDate today = today();
            LocalDate end = optDate(in, "end_date").orElse(today);
            LocalDate start = optDate(in, "start_date").orElse(end.minusDays(90));
            if (start.isAfter(end)) {
                throw new ToolInputException("start_date deve ser anterior a end_date.");
            }
            TransactionType type = optEnum(in, "type", TransactionType.class).orElse(null);
            String text = optString(in, "text").map(AssistantToolbox::normalize).orElse(null);

            List<Map<String, Object>> rows = transactionRepository
                .findByFilters(userId, start, end, type, null, PageRequest.of(0, 200))
                .getContent().stream()
                .filter(t -> text == null
                    || normalize(nullToEmpty(t.getDescription())).contains(text)
                    || (t.getCategory() != null && normalize(t.getCategory().getName()).contains(text)))
                .limit(MAX_SEARCH_RESULTS)
                .map(this::transactionRow)
                .toList();

            return ok(Map.of("count", rows.size(), "transactions", rows));
        }

        private AiToolResult listGoals() {
            List<Map<String, Object>> goals = goalRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getId().toString());
                    m.put("name", g.getName());
                    m.put("target_amount", g.getTargetAmount());
                    m.put("saved_amount", g.getSavedAmount());
                    m.put("deadline", g.getDeadline().toString());
                    m.put("status", g.getStatus().name());
                    return m;
                })
                .toList();
            return ok(Map.of("goals", goals));
        }

        // ── Propostas ───────────────────────────────────────────────────────

        private AiToolResult proposeCreateTransaction(Map<String, Object> in) {
            TransactionType type = reqEnum(in, "type", TransactionType.class);
            Category category = resolveCategory(reqString(in, "category_name"), type);

            TransactionRequest request = new TransactionRequest();
            request.setAmount(reqAmount(in, "amount"));
            request.setType(type);
            request.setCategoryId(category.getId());
            request.setDescription(reqString(in, "description"));
            request.setTransactionDate(optDate(in, "transaction_date").orElse(today()));
            request.setFrequency(optEnum(in, "frequency", ExpenseFrequency.class).orElse(ExpenseFrequency.ONCE));
            int repeat = optInt(in, "repeat_count").orElse(1);
            if (repeat < 1 || repeat > 120) {
                throw new ToolInputException("repeat_count deve estar entre 1 e 120.");
            }
            if (request.getFrequency() == ExpenseFrequency.ONCE) {
                repeat = 1;
            }
            request.setRepeatCount(repeat);
            validate(request);

            String summary = String.format("Adicionar %s de %s em %s (%s) em %s%s",
                type == TransactionType.INCOME ? "receita" : "despesa",
                money(request.getAmount()), category.getName(), request.getDescription(),
                request.getTransactionDate().format(BR_DATE),
                repeat > 1 ? String.format(", repetindo %d vezes (%s)", repeat, frequencyLabel(request.getFrequency())) : "");

            return propose(ProposedActionType.CREATE_TRANSACTION, summary, toMap(request));
        }

        private AiToolResult proposeUpdateTransaction(Map<String, Object> in) {
            Transaction existing = findOwnTransaction(reqString(in, "transaction_id"));

            TransactionType type = optEnum(in, "type", TransactionType.class).orElse(existing.getType());
            Category category = optString(in, "category_name")
                .map(name -> resolveCategory(name, type))
                .orElse(existing.getCategory());

            TransactionRequest request = new TransactionRequest();
            request.setAmount(in.containsKey("amount") ? reqAmount(in, "amount") : existing.getAmount());
            request.setType(type);
            request.setCategoryId(category.getId());
            request.setDescription(optString(in, "description").orElse(existing.getDescription()));
            request.setTransactionDate(optDate(in, "transaction_date").orElse(existing.getTransactionDate()));
            request.setFrequency(existing.getFrequency());
            request.setRepeatCount(1);
            request.setNotes(existing.getNotes());
            request.setDueDayOfMonth(existing.getDueDayOfMonth());
            validate(request);

            List<String> changes = new ArrayList<>();
            if (request.getAmount().compareTo(existing.getAmount()) != 0)
                changes.add("valor " + money(existing.getAmount()) + " → " + money(request.getAmount()));
            if (!category.getId().equals(existing.getCategory().getId()))
                changes.add("categoria " + existing.getCategory().getName() + " → " + category.getName());
            if (!nullToEmpty(request.getDescription()).equals(nullToEmpty(existing.getDescription())))
                changes.add("descrição \"" + nullToEmpty(request.getDescription()) + "\"");
            if (!request.getTransactionDate().equals(existing.getTransactionDate()))
                changes.add("data " + request.getTransactionDate().format(BR_DATE));
            if (type != existing.getType())
                changes.add(type == TransactionType.INCOME ? "tipo receita" : "tipo despesa");
            if (changes.isEmpty()) {
                throw new ToolInputException("Nenhuma alteração em relação ao lançamento atual.");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("transactionId", existing.getId().toString());
            payload.putAll(toMap(request));

            String summary = "Editar \"" + label(existing) + "\": " + String.join(", ", changes);
            return propose(ProposedActionType.UPDATE_TRANSACTION, summary, payload);
        }

        private AiToolResult proposeDeleteTransaction(Map<String, Object> in) {
            Transaction existing = findOwnTransaction(reqString(in, "transaction_id"));
            String summary = "Apagar " + (existing.getType() == TransactionType.INCOME ? "receita" : "despesa")
                + " \"" + label(existing) + "\" de " + money(existing.getAmount())
                + " em " + existing.getTransactionDate().format(BR_DATE);
            return propose(ProposedActionType.DELETE_TRANSACTION, summary,
                Map.of("transactionId", existing.getId().toString()));
        }

        private AiToolResult proposeCreateGoal(Map<String, Object> in) {
            GoalRequest request = new GoalRequest();
            request.setName(reqString(in, "name"));
            request.setTargetAmount(reqAmount(in, "target_amount"));
            request.setDeadline(optDate(in, "deadline")
                .orElseThrow(() -> new ToolInputException("deadline é obrigatório (YYYY-MM-DD).")));
            request.setIcon(optString(in, "icon").orElse(null));
            validate(request);
            if (!request.getDeadline().isAfter(today())) {
                throw new ToolInputException("deadline deve ser uma data futura.");
            }

            String summary = String.format("Criar meta \"%s\": juntar %s até %s",
                request.getName(), money(request.getTargetAmount()), request.getDeadline().format(BR_DATE));
            return propose(ProposedActionType.CREATE_GOAL, summary, toMap(request));
        }

        private AiToolResult proposeContribution(Map<String, Object> in) {
            UUID goalId = parseUuid(reqString(in, "goal_id"), "goal_id");
            Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ToolInputException("Meta não encontrada."));
            if (goal.getStatus() != GoalStatus.ACTIVE) {
                throw new ToolInputException("A meta \"" + goal.getName() + "\" não está ativa.");
            }

            GoalContributionRequest request = new GoalContributionRequest();
            request.setAmount(reqAmount(in, "amount"));
            validate(request);

            String summary = String.format("Guardar %s na meta \"%s\" (%s de %s até agora)",
                money(request.getAmount()), goal.getName(),
                money(goal.getSavedAmount()), money(goal.getTargetAmount()));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("goalId", goal.getId().toString());
            payload.put("amount", request.getAmount());
            return propose(ProposedActionType.CONTRIBUTE_GOAL, summary, payload);
        }

        private AiToolResult propose(ProposedActionType type, String summary, Map<String, Object> payload) {
            ProposedActionResponse action = ProposedActionResponse.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .summary(summary)
                .payload(payload)
                .build();
            proposals.add(action);
            return ok(Map.of(
                "status", "PENDING_USER_CONFIRMATION",
                "summary", summary,
                "note", "Nada foi salvo. O usuário verá um cartão para confirmar."));
        }

        // ── Auxiliares ──────────────────────────────────────────────────────

        private Transaction findOwnTransaction(String rawId) {
            UUID id = parseUuid(rawId, "transaction_id");
            return transactionRepository.findByIdAndUserId(id, userId)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new ToolInputException("Lançamento não encontrado."));
        }

        private Category resolveCategory(String name, TransactionType type) {
            List<Category> available = categoryRepository.findAllAvailableForUserAndType(userId, type);
            if (available.isEmpty()) {
                throw new ToolInputException("Nenhuma categoria disponível para esse tipo.");
            }
            String wanted = normalize(name);
            return available.stream().filter(c -> normalize(c.getName()).equals(wanted)).findFirst()
                .or(() -> available.stream().filter(c -> {
                    String n = normalize(c.getName());
                    return n.contains(wanted) || wanted.contains(n);
                }).findFirst())
                .or(() -> available.stream().filter(c -> normalize(c.getName()).startsWith("outro")).findFirst())
                .orElseThrow(() -> new ToolInputException("Categoria \"" + name + "\" não encontrada. Opções: "
                    + available.stream().map(Category::getName).collect(Collectors.joining(", "))));
        }

        private Map<String, Object> transactionRow(Transaction t) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId().toString());
            m.put("date", t.getTransactionDate().toString());
            m.put("type", t.getType().name());
            m.put("amount", t.getAmount());
            m.put("category", t.getCategory() != null ? t.getCategory().getName() : null);
            m.put("description", t.getDescription());
            m.put("recurring", t.getRecurrenceGroupId() != null);
            return m;
        }

        private void validate(Object request) {
            Set<ConstraintViolation<Object>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ToolInputException(violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
            }
        }

        private Map<String, Object> toMap(Object request) {
            Map<String, Object> map = objectMapper.convertValue(request, MAP_TYPE);
            map.values().removeIf(java.util.Objects::isNull);
            return map;
        }

        private LocalDate today() {
            return LocalDate.now(clock.withZone(chatProperties.getTimezone()));
        }

        private AiToolResult ok(Object body) {
            return AiToolResult.ok(json(body));
        }
    }

    // ── Leitura de parâmetros ───────────────────────────────────────────────

    private AiToolResult error(String message) {
        return AiToolResult.error(json(Map.of("error", message)));
    }

    private String json(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"falha ao serializar\"}";
        }
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Optional<String> optString(Map<String, Object> in, String key) {
        Object value = in.get(key);
        if (value == null) return Optional.empty();
        String s = value.toString().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    private static String reqString(Map<String, Object> in, String key) {
        return optString(in, key).orElseThrow(() -> new ToolInputException(key + " é obrigatório."));
    }

    private static BigDecimal reqAmount(Map<String, Object> in, String key) {
        String raw = reqString(in, key);
        try {
            BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (value.signum() <= 0) throw new ToolInputException(key + " deve ser maior que zero.");
            return value;
        } catch (NumberFormatException e) {
            throw new ToolInputException(key + " deve ser um número (ex.: 49.90).");
        }
    }

    private static Optional<Integer> optInt(Map<String, Object> in, String key) {
        return optString(in, key).map(s -> {
            try {
                return new BigDecimal(s).intValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new ToolInputException(key + " deve ser um número inteiro.");
            }
        });
    }

    private static Optional<LocalDate> optDate(Map<String, Object> in, String key) {
        return optString(in, key).map(s -> {
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                throw new ToolInputException(key + " deve estar no formato YYYY-MM-DD.");
            }
        });
    }

    private static <E extends Enum<E>> Optional<E> optEnum(Map<String, Object> in, String key, Class<E> type) {
        return optString(in, key).map(s -> {
            try {
                return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ToolInputException(key + " inválido: " + s);
            }
        });
    }

    private static <E extends Enum<E>> E reqEnum(Map<String, Object> in, String key, Class<E> type) {
        return optEnum(in, key, type).orElseThrow(() -> new ToolInputException(key + " é obrigatório."));
    }

    private static UUID parseUuid(String raw, String key) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ToolInputException(key + " inválido.");
        }
    }

    static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String label(Transaction t) {
        if (t.getDescription() != null && !t.getDescription().isBlank()) return t.getDescription();
        return t.getCategory() != null ? t.getCategory().getName() : "lançamento";
    }

    private static String frequencyLabel(ExpenseFrequency f) {
        return switch (f) {
            case DAILY -> "diário";
            case WEEKLY -> "semanal";
            case MONTHLY -> "mensal";
            case QUARTERLY -> "trimestral";
            case YEARLY -> "anual";
            default -> "único";
        };
    }

    /** Erro de parâmetro que volta para a IA corrigir. */
    static class ToolInputException extends RuntimeException {
        ToolInputException(String message) {
            super(message);
        }
    }
}
