package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.TransactionRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.CategorySummaryResponse;
import com.finny.api.application.dto.response.TransactionResponse;
import com.finny.api.application.dto.response.TransactionSummaryResponse;
import com.finny.api.application.service.IdempotencyService;
import com.finny.api.application.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Endpoints de transações")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @Operation(summary = "Listar transações do usuário",
               description = "Aceita filtros opcionais startDate e endDate (yyyy-MM-dd). " +
                             "Sem filtros, retorna transações até hoje (esconde recorrências futuras).")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        Page<TransactionResponse> response = transactionService.getTransactions(userId, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Criar nova transação",
               description = "Aceita o cabeçalho Idempotency-Key: repetir a mesma chave devolve a transação criada da primeira vez.")
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = IdempotencyService.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        return idempotencyService.execute(userId, idempotencyKey, "POST /transactions", HttpStatus.CREATED,
            TransactionResponse.class, () -> transactionService.createTransaction(userId, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter transação por ID")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        TransactionResponse response = transactionService.getTransactionById(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        TransactionResponse response = transactionService.updateTransaction(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar transação (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        transactionService.deleteTransaction(userId, id);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Transação deletada com sucesso")
                .build());
    }

    @DeleteMapping("/{id}/recurrence")
    @Operation(summary = "Deletar recorrências",
               description = "mode=ALL apaga todos os meses do grupo. " +
                             "mode=THIS_AND_FUTURE apaga este mês e os seguintes.")
    public ResponseEntity<ApiResponse<Void>> deleteRecurrence(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ALL") String mode) {

        if (!mode.equalsIgnoreCase("ALL") && !mode.equalsIgnoreCase("THIS_AND_FUTURE")) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false)
                    .message("mode deve ser ALL ou THIS_AND_FUTURE")
                    .build());
        }

        UUID userId = UUID.fromString(userDetails.getUsername());
        int deleted = transactionService.deleteRecurrence(userId, id, mode);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message(deleted + " lançamento(s) excluído(s)")
                .build());
    }

    @GetMapping("/summary")
    @Operation(summary = "Obter resumo de transações")
    public ResponseEntity<TransactionSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        TransactionSummaryResponse response = transactionService.getSummary(userId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Resumo de gastos agrupados por categoria")
    public ResponseEntity<CategorySummaryResponse> getCategorySummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        CategorySummaryResponse response = transactionService.getCategorySummary(userId, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
