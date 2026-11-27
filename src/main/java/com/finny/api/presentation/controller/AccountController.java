package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.DeleteAccountRequest;
import com.finny.api.application.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Direitos do titular (LGPD): baixar meus dados e excluir minha conta. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Exportar dados e excluir conta (LGPD)")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/export")
    @Operation(summary = "Baixar uma cópia de todos os meus dados (JSON)")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> data = accountService.exportData(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-finny.json\"")
            .body(data);
    }

    /** POST (e não DELETE) porque leva a senha no corpo. */
    @PostMapping("/delete")
    @Operation(summary = "Excluir minha conta e todos os meus dados (pede a senha)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(UUID.fromString(userDetails.getUsername()), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
