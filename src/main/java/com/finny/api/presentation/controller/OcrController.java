package com.finny.api.presentation.controller;

import com.finny.api.application.dto.response.OcrStatusResponse;
import com.finny.api.application.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR", description = "Endpoints de processamento OCR de notas fiscais")
@SecurityRequirement(name = "bearerAuth")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/process")
    @Operation(summary = "Enviar imagem de nota fiscal para processamento OCR")
    public ResponseEntity<OcrStatusResponse> processReceipt(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("image") MultipartFile image) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        OcrStatusResponse response = ocrService.processReceipt(userId, image);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/status/{processingId}")
    @Operation(summary = "Verificar status do processamento OCR")
    public ResponseEntity<OcrStatusResponse> getStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID processingId) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        OcrStatusResponse response = ocrService.getStatus(userId, processingId);
        return ResponseEntity.ok(response);
    }
}
