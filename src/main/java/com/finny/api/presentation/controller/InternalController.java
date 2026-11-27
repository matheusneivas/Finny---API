package com.finny.api.presentation.controller;

import com.finny.api.application.dto.request.OcrCallbackRequest;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
@Hidden // Ocultar do Swagger
public class InternalController {

    // TODO: Implementar validação de X-Internal-API-Key header

    @PostMapping("/callbacks/ocr/{callbackToken}")
    public ResponseEntity<Void> ocrCallback(
            @PathVariable String callbackToken,
            @RequestBody OcrCallbackRequest ocrResult) {

        log.info("Callback de OCR recebido"); // o token é segredo: não vai para o log

        // TODO: Implementar lógica de callback
        // 1. Buscar OcrProcessingQueue pelo callbackToken
        // 2. Criar Transaction com os dados do OCR
        // 3. Atualizar status do processamento
        // 4. Vincular Transaction criada ao OcrProcessingQueue

        return ResponseEntity.ok().build();
    }
}
