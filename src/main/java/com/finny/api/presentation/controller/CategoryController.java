package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.CategoryRequest;
import com.finny.api.application.dto.response.ApiResponse;
import com.finny.api.application.dto.response.CategoryResponse;
import com.finny.api.application.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Endpoints de categorias")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Listar categorias (padrão + personalizadas)")
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<CategoryResponse> response = categoryService.getCategories(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Criar nova categoria personalizada")
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CategoryRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        CategoryResponse response = categoryService.createCategory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar categoria personalizada")
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        CategoryResponse response = categoryService.updateCategory(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar categoria personalizada")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        categoryService.deleteCategory(userId, id);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Categoria deletada com sucesso")
                .build());
    }
}
