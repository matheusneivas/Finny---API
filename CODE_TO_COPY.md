# 📋 CÓDIGO PRONTO PARA COPIAR

Este arquivo contém todo o código que precisa ser **manualmente copiado** para completar as melhorias da API.

---

## 1. GoalProgressResponse.java (CRIAR NOVO ARQUIVO)

**Caminho:** `src/main/java/com/finny/api/application/dto/response/GoalProgressResponse.java`

```java
package com.finny.api.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalProgressResponse {
    private UUID goalId;
    private BigDecimal progress;        // Porcentagem (0-100)
    private BigDecimal remaining;       // Quanto falta
    private BigDecimal monthlyRequired; // Quanto economizar por mês
    private Long monthsRemaining;       // Meses até deadline
    private Boolean isOverdue;          // Se está atrasada
}
```

---

## 2. GoalService.java (ADICIONAR MÉTODOS)

**Adicione estes imports:**
```java
import java.time.temporal.ChronoUnit;
```

**Adicione estas constantes:**
```java
private static final long MAX_ACTIVE_GOALS = 10;
```

**Adicione estes métodos:**

```java
/**
 * Calcula o progresso detalhado de uma meta
 */
@Transactional(readOnly = true)
public GoalProgressResponse calculateProgress(UUID goalId, UUID userId) {
    log.debug("Calculating progress for goal: {} | user: {}", goalId, userId);

    Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
        .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

    // Calcula quanto falta
    BigDecimal remaining = goal.getTargetAmount().subtract(goal.getSavedAmount());

    // Calcula porcentagem de progresso
    BigDecimal progress = BigDecimal.ZERO;
    if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
        progress = goal.getSavedAmount()
            .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    // Calcula meses restantes
    long monthsRemaining = ChronoUnit.MONTHS.between(LocalDate.now(), goal.getDeadline());
    if (monthsRemaining < 0) {
        monthsRemaining = 0;
    }

    // Calcula quanto precisa economizar por mês
    BigDecimal monthlyRequired = BigDecimal.ZERO;
    if (monthsRemaining > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
        monthlyRequired = remaining.divide(
            BigDecimal.valueOf(monthsRemaining),
            2,
            RoundingMode.HALF_UP
        );
    } else if (monthsRemaining == 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
        monthlyRequired = remaining;
    }

    log.info("Progress calculated | goal: {} | progress: {}% | remaining: {} | monthly: {}",
             goalId, progress, remaining, monthlyRequired);

    return GoalProgressResponse.builder()
        .goalId(goalId)
        .progress(progress)
        .remaining(remaining)
        .monthlyRequired(monthlyRequired)
        .monthsRemaining(monthsRemaining)
        .isOverdue(goal.getDeadline().isBefore(LocalDate.now()) && goal.getStatus() == GoalStatus.ACTIVE)
        .build();
}

/**
 * Verifica se a meta foi completada e atualiza status automaticamente
 */
@Transactional
public void checkAndCompleteGoal(UUID goalId, UUID userId) {
    Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
        .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

    if (goal.getSavedAmount().compareTo(goal.getTargetAmount()) >= 0
        && goal.getStatus() == GoalStatus.ACTIVE) {

        goal.setStatus(GoalStatus.COMPLETED);
        goal.setCompletedAt(LocalDateTime.now());
        goalRepository.save(goal);

        log.info("Goal completed automatically | goalId: {} | user: {}", goalId, userId);
    }
}

/**
 * Valida regras de negócio ao criar/atualizar meta
 */
private void validateGoalBusinessRules(Goal goal, UUID userId) {
    // Validar limite de metas ativas
    long activeGoalsCount = goalRepository.countActiveByUserId(userId);
    if (goal.getStatus() == GoalStatus.ACTIVE && activeGoalsCount >= MAX_ACTIVE_GOALS) {
        throw new BusinessException("Você já possui " + MAX_ACTIVE_GOALS + " metas ativas. Complete ou arquive alguma para criar uma nova.");
    }

    // Validar valor alvo positivo
    if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("Valor alvo deve ser maior que zero");
    }

    // Validar valor economizado não negativo
    if (goal.getSavedAmount().compareTo(BigDecimal.ZERO) < 0) {
        throw new BusinessException("Valor economizado não pode ser negativo");
    }

    // Validar valor economizado não maior que alvo
    if (goal.getSavedAmount().compareTo(goal.getTargetAmount()) > 0) {
        throw new BusinessException("Valor economizado não pode ser maior que o valor alvo");
    }

    // Validar deadline no futuro (se meta ativa)
    if (goal.getStatus() == GoalStatus.ACTIVE && goal.getDeadline().isBefore(LocalDate.now())) {
        throw new BusinessException("Data limite deve ser no futuro para metas ativas");
    }
}
```

**Adicione logs estruturados nos métodos existentes (exemplo):**
```java
@Transactional
public GoalResponse createGoal(UUID userId, GoalRequest request) {
    log.info("Creating goal | user: {} | name: {} | target: {}",
             userId, request.getName(), request.getTargetAmount());

    try {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Goal goal = goalMapper.toEntity(request);
        goal.setUser(user);
        goal.setSavedAmount(BigDecimal.ZERO);
        goal.setStatus(GoalStatus.ACTIVE);

        // ADICIONAR ESTA LINHA:
        validateGoalBusinessRules(goal, userId);

        if (request.getRelatedCategoryId() != null) {
            Category category = categoryRepository.findById(request.getRelatedCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

            if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
                throw new BusinessException("Categoria não pertence ao usuário");
            }

            goal.setRelatedCategory(category);
        }

        goal = goalRepository.save(goal);

        log.info("Goal created successfully | goalId: {} | user: {}", goal.getId(), userId);

        return goalMapper.toResponse(goal);

    } catch (BusinessException e) {
        log.warn("Business rule violation | user: {} | error: {}", userId, e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Failed to create goal | user: {} | error: {}", userId, e.getMessage(), e);
        throw new BusinessException("Erro ao criar meta. Tente novamente.");
    }
}
```

---

## 3. TransactionService.java (ADICIONAR MÉTODOS)

```java
/**
 * Valida se a categoria existe e se o usuário tem acesso a ela
 */
private void validateCategoryAccess(UUID categoryId, UUID userId) {
    log.debug("Validating category access | categoryId: {} | user: {}", categoryId, userId);

    boolean hasAccess = categoryRepository.isAccessibleByUser(categoryId, userId);

    if (!hasAccess) {
        log.warn("Unauthorized category access | user: {} | categoryId: {}", userId, categoryId);
        throw new UnauthorizedException("Você não tem permissão para usar esta categoria");
    }
}

/**
 * Valida se a transação pertence ao usuário (tenant isolation)
 */
private void validateTransactionOwnership(UUID transactionId, UUID userId) {
    log.debug("Validating transaction ownership | transactionId: {} | user: {}", transactionId, userId);

    boolean isOwner = transactionRepository.existsByIdAndUserId(transactionId, userId);

    if (!isOwner) {
        log.warn("Unauthorized transaction access | user: {} | transactionId: {}", userId, transactionId);
        throw new UnauthorizedException("Você não tem permissão para acessar esta transação");
    }
}

/**
 * Valida regras de negócio da transação
 */
private void validateTransactionBusinessRules(Transaction transaction) {
    if (transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException("Valor da transação deve ser maior que zero");
    }

    if (transaction.getTransactionDate().isAfter(LocalDate.now())) {
        throw new BusinessException("Data da transação não pode ser futura");
    }

    if (transaction.getType() == null) {
        throw new BusinessException("Tipo de transação é obrigatório");
    }
}
```

**Adicione validações nos métodos existentes:**
```java
@Transactional
public TransactionResponse createTransaction(UUID userId, TransactionRequest request) {
    log.info("Creating transaction | user: {} | type: {} | amount: {} | category: {}",
             userId, request.getType(), request.getAmount(), request.getCategoryId());

    try {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // ADICIONAR ESTA LINHA:
        validateCategoryAccess(request.getCategoryId(), userId);

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        Transaction transaction = transactionMapper.toEntity(request);
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setSource(TransactionSource.MANUAL);

        // ADICIONAR ESTA LINHA:
        validateTransactionBusinessRules(transaction);

        transaction = transactionRepository.save(transaction);

        log.info("Transaction created successfully | transactionId: {} | user: {}",
                 transaction.getId(), userId);

        return transactionMapper.toResponse(transaction);

    } catch (UnauthorizedException | BusinessException e) {
        log.warn("Validation failed | user: {} | error: {}", userId, e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Failed to create transaction | user: {} | error: {}", userId, e.getMessage(), e);
        throw new BusinessException("Erro ao criar transação. Tente novamente.");
    }
}

@Transactional
public void deleteTransaction(UUID userId, UUID transactionId) {
    log.info("Deleting transaction (soft delete) | transactionId: {} | user: {}", transactionId, userId);

    // ADICIONAR ESTA LINHA:
    validateTransactionOwnership(transactionId, userId);

    Transaction transaction = transactionRepository.findById(transactionId)
        .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada"));

    if (transaction.getDeleted()) {
        throw new EntityNotFoundException("Transação não encontrada");
    }

    transaction.setDeleted(true);
    transactionRepository.save(transaction);

    log.info("Transaction soft deleted | transactionId: {} | user: {}", transactionId, userId);
}
```

---

## 4. CategoryService.java (ADICIONAR MÉTODOS E CONSTANTE)

**Adicione a constante:**
```java
private static final long MAX_CUSTOM_CATEGORIES = 50;
```

**Injete TransactionRepository:**
```java
private final TransactionRepository transactionRepository;
```

**Adicione os métodos:**
```java
/**
 * Valida se categoria pode ser deletada
 */
@Transactional(readOnly = true)
public void validateCategoryDeletion(UUID categoryId, UUID userId) {
    log.debug("Validating category deletion | categoryId: {} | user: {}", categoryId, userId);

    Category category = categoryRepository.findById(categoryId)
        .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

    if (category.getIsDefault()) {
        log.warn("Attempt to delete default category | user: {} | categoryId: {}", userId, categoryId);
        throw new BusinessException("Categorias padrão não podem ser deletadas");
    }

    if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
        log.warn("Unauthorized category deletion | user: {} | categoryId: {}", userId, categoryId);
        throw new UnauthorizedException("Você não tem permissão para deletar esta categoria");
    }

    List<Transaction> transactions = transactionRepository.findByCategoryId(userId, categoryId);
    if (!transactions.isEmpty()) {
        log.warn("Category in use | categoryId: {} | transactionCount: {}", categoryId, transactions.size());
        throw new BusinessException(
            String.format("Esta categoria não pode ser deletada pois possui %d transação(ões) vinculada(s)",
                          transactions.size())
        );
    }
}

/**
 * Valida se usuário pode criar nova categoria personalizada
 */
private void validateCategoryCreation(UUID userId, String categoryName) {
    long customCategoriesCount = categoryRepository.countCustomByUserId(userId);
    if (customCategoriesCount >= MAX_CUSTOM_CATEGORIES) {
        throw new BusinessException(
            String.format("Você já possui %d categorias personalizadas. Limite máximo: %d",
                          customCategoriesCount, MAX_CUSTOM_CATEGORIES)
        );
    }

    boolean nameExists = categoryRepository.existsByUserIdAndNameIgnoreCase(userId, categoryName);
    if (nameExists) {
        throw new ResourceConflictException("Você já possui uma categoria com este nome");
    }
}

/**
 * Valida se categoria pode ser editada
 */
private void validateCategoryUpdate(UUID categoryId, UUID userId, String newName) {
    Category category = categoryRepository.findById(categoryId)
        .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

    if (category.getIsDefault()) {
        throw new BusinessException("Categorias padrão não podem ser editadas");
    }

    if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
        throw new UnauthorizedException("Você não tem permissão para editar esta categoria");
    }

    if (!category.getName().equalsIgnoreCase(newName)) {
        boolean nameExists = categoryRepository.existsByUserIdAndNameIgnoreCase(userId, newName);
        if (nameExists) {
            throw new ResourceConflictException("Você já possui uma categoria com este nome");
        }
    }
}
```

**Adicione validações nos métodos existentes:**
```java
@Transactional
public CategoryResponse createCategory(UUID userId, CategoryRequest request) {
    log.info("Creating category | user: {} | name: {}", userId, request.getName());

    try {
        // ADICIONAR ESTA LINHA:
        validateCategoryCreation(userId, request.getName());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Category category = categoryMapper.toEntity(request);
        category.setUser(user);
        category.setIsDefault(false);

        category = categoryRepository.save(category);

        log.info("Category created successfully | categoryId: {} | user: {}", category.getId(), userId);

        return categoryMapper.toResponse(category);

    } catch (ResourceConflictException | BusinessException e) {
        log.warn("Validation failed | user: {} | error: {}", userId, e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Failed to create category | user: {} | error: {}", userId, e.getMessage(), e);
        throw new BusinessException("Erro ao criar categoria. Tente novamente.");
    }
}

@Transactional
public void deleteCategory(UUID userId, UUID categoryId) {
    log.info("Deleting category | categoryId: {} | user: {}", categoryId, userId);

    // ADICIONAR ESTA LINHA:
    validateCategoryDeletion(categoryId, userId);

    Category category = categoryRepository.findById(categoryId)
        .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

    categoryRepository.delete(category);

    log.info("Category deleted successfully | categoryId: {} | user: {}", categoryId, userId);
}
```

---

## 5. OcrService.java (ADICIONAR MÉTODOS E CONSTANTES)

**Adicione as constantes:**
```java
private static final long FREE_OCR_LIMIT = 5;
private static final long PREMIUM_OCR_LIMIT = 50;
```

**Adicione imports:**
```java
import java.time.YearMonth;
import com.finny.api.domain.enums.UserPlan;
```

**Adicione os métodos:**
```java
/**
 * Verifica se usuário pode processar mais notas fiscais este mês
 */
public void checkOcrLimit(UUID userId) {
    log.debug("Checking OCR limit | user: {}", userId);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

    YearMonth currentMonth = YearMonth.now();
    LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();

    long processedThisMonth = ocrQueueRepository.countCompletedInCurrentMonth(userId, startOfMonth);

    long limit = (user.getPlan() == UserPlan.PREMIUM) ? PREMIUM_OCR_LIMIT : FREE_OCR_LIMIT;

    log.info("OCR usage | user: {} | count: {}/{} | plan: {}",
             userId, processedThisMonth, limit, user.getPlan());

    if (processedThisMonth >= limit) {
        String message = (user.getPlan() == UserPlan.FREE)
            ? String.format("Você atingiu o limite de %d notas fiscais gratuitas este mês. Faça upgrade para Premium!", FREE_OCR_LIMIT)
            : String.format("Você atingiu o limite de %d notas fiscais este mês.", PREMIUM_OCR_LIMIT);

        log.warn("OCR limit exceeded | user: {} | count: {} | limit: {}", userId, processedThisMonth, limit);
        throw new BusinessException(message);
    }
}

/**
 * Retorna quantas notas o usuário ainda pode processar este mês
 */
public long getRemainingOcrCount(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

    YearMonth currentMonth = YearMonth.now();
    LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();

    long processedThisMonth = ocrQueueRepository.countCompletedInCurrentMonth(userId, startOfMonth);
    long limit = (user.getPlan() == UserPlan.PREMIUM) ? PREMIUM_OCR_LIMIT : FREE_OCR_LIMIT;

    return Math.max(0, limit - processedThisMonth);
}
```

**Adicione validação no método processReceipt:**
```java
@Transactional
public OcrStatusResponse processReceipt(UUID userId, MultipartFile image) {
    log.info("Processing OCR | user: {} | filename: {}", userId, image.getOriginalFilename());

    // ADICIONAR ESTA LINHA NO INÍCIO:
    checkOcrLimit(userId);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

    // ... resto do código
}
```

---

## 6. CHECKLIST FINAL

Depois de copiar todo o código acima, verifique:

### GoalService.java
- [ ] Import `java.time.temporal.ChronoUnit` adicionado
- [ ] Constante `MAX_ACTIVE_GOALS` adicionada
- [ ] Método `calculateProgress()` adicionado
- [ ] Método `checkAndCompleteGoal()` adicionado
- [ ] Método `validateGoalBusinessRules()` adicionado
- [ ] Logs estruturados adicionados em `createGoal()`, `updateGoal()`, `deleteGoal()`
- [ ] Validação chamada no `createGoal()`

### TransactionService.java
- [ ] Método `validateCategoryAccess()` adicionado
- [ ] Método `validateTransactionOwnership()` adicionado
- [ ] Método `validateTransactionBusinessRules()` adicionado
- [ ] Validações chamadas em `createTransaction()`
- [ ] Validação chamada em `deleteTransaction()`
- [ ] Logs estruturados adicionados

### CategoryService.java
- [ ] Constante `MAX_CUSTOM_CATEGORIES` adicionada
- [ ] `TransactionRepository` injetado
- [ ] Método `validateCategoryDeletion()` adicionado
- [ ] Método `validateCategoryCreation()` adicionado
- [ ] Método `validateCategoryUpdate()` adicionado
- [ ] Validações chamadas em `createCategory()`, `deleteCategory()`
- [ ] Logs estruturados adicionados

### OcrService.java
- [ ] Constantes `FREE_OCR_LIMIT` e `PREMIUM_OCR_LIMIT` adicionadas
- [ ] Imports adicionados
- [ ] Método `checkOcrLimit()` adicionado
- [ ] Método `getRemainingOcrCount()` adicionado
- [ ] Validação chamada em `processReceipt()`
- [ ] Logs estruturados adicionados

### GoalProgressResponse.java
- [ ] Arquivo criado em `src/main/java/com/finny/api/application/dto/response/`
- [ ] Todos os campos definidos corretamente

---

## 🎉 PRONTO!

Depois de copiar todo esse código, a API Finny estará completa com:

✅ CORS configurado
✅ 19 queries customizadas
✅ Validações de negócio completas
✅ Controle de limites (metas, categorias, OCR)
✅ Logs estruturados
✅ Tenant isolation reforçado

**Compile e teste!** 🚀
