package com.finny.api.application.service;

import com.finny.api.application.dto.response.OcrStatusResponse;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.application.mapper.TransactionMapper;
import com.finny.api.domain.entity.OcrProcessingQueue;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.ProcessingStatus;
import com.finny.api.domain.repository.OcrProcessingQueueRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final OcrProcessingQueueRepository ocrProcessingQueueRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;
    // TODO: Injetar ImageStorageService e N8nClient quando implementados

    @Transactional
    public OcrStatusResponse processReceipt(UUID userId, MultipartFile image) {
        log.debug("Processing OCR for user: {}", userId);

        validateImage(image);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // TODO: Salvar imagem no storage
        String imageStoragePath = "/tmp/finny-images/" + UUID.randomUUID() + ".jpg";

        // Gerar callback token único
        String callbackToken = UUID.randomUUID().toString();

        // Criar registro na fila
        OcrProcessingQueue queue = new OcrProcessingQueue();
        queue.setUser(user);
        queue.setImageStoragePath(imageStoragePath);
        queue.setCallbackToken(callbackToken);
        queue.setStatus(ProcessingStatus.PENDING);

        queue = ocrProcessingQueueRepository.save(queue);

        // TODO: Chamar n8n para processar OCR de forma assíncrona
        log.info("OCR processing queued: {}", queue.getId());

        return OcrStatusResponse.builder()
                .processingId(queue.getId())
                .status(queue.getStatus())
                .createdAt(queue.getCreatedAt())
                .build();
    }

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/heic");

    /** Aceita só imagens pequenas e confere a assinatura do arquivo (não confia só no Content-Type). */
    static void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ValidationException("Envie uma imagem");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new ValidationException("Imagem deve ter no máximo 5 MB");
        }
        String type = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ValidationException("Formato não suportado. Use JPEG, PNG, WEBP ou HEIC");
        }
        try (java.io.InputStream in = image.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (!looksLikeImage(head)) {
                throw new ValidationException("Arquivo não parece ser uma imagem válida");
            }
        } catch (java.io.IOException e) {
            throw new ValidationException("Não foi possível ler a imagem");
        }
    }

    private static boolean looksLikeImage(byte[] h) {
        if (h.length < 12) return false;
        boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E';
        boolean heic = h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        return jpeg || png || webp || heic;
    }

    @Transactional(readOnly = true)
    public OcrStatusResponse getStatus(UUID userId, UUID processingId) {
        log.debug("Getting OCR status: {} for user: {}", processingId, userId);

        OcrProcessingQueue queue = ocrProcessingQueueRepository.findByIdAndUserId(processingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Processamento não encontrado"));

        OcrStatusResponse.OcrStatusResponseBuilder responseBuilder = OcrStatusResponse.builder()
                .processingId(queue.getId())
                .status(queue.getStatus())
                .errorMessage(queue.getErrorMessage())
                .createdAt(queue.getCreatedAt())
                .processedAt(queue.getProcessedAt());

        if (queue.getCreatedTransaction() != null) {
            responseBuilder.createdTransaction(transactionMapper.toResponse(queue.getCreatedTransaction()));
        }

        return responseBuilder.build();
    }
}
