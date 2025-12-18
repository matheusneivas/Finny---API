package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.GoalContributionRequest;
import com.finny.api.application.dto.request.GoalRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.GoalProgressResponse;
import com.finny.api.application.dto.response.GoalResponse;
import com.finny.api.application.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Endpoints de metas financeiras")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    @Operation(summary = "Listar metas do usuário")
    public ResponseEntity<List<GoalResponse>> getGoals(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<GoalResponse> response = goalService.getGoals(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Criar nova meta")
    public ResponseEntity<GoalResponse> createGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GoalRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        GoalResponse response = goalService.createGoal(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter meta por ID")
    public ResponseEntity<GoalResponse> getGoalById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        GoalResponse response = goalService.getGoalById(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar meta")
    public ResponseEntity<GoalResponse> updateGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody GoalRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        GoalResponse response = goalService.updateGoal(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar meta")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        goalService.deleteGoal(userId, id);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Meta deletada com sucesso")
                .build());
    }

    @PostMapping("/{id}/contribute")
    @Operation(summary = "Adicionar contribuição à meta")
    public ResponseEntity<GoalProgressResponse> contributeToGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody GoalContributionRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        GoalProgressResponse response = goalService.contributeToGoal(userId, id, request);
        return ResponseEntity.ok(response);
    }
}
