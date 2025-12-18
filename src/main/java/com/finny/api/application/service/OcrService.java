package com.finny.api.application.service;

import com.finny.api.application.dto.response.OcrStatusResponse;
import com.finny.api.application.exception.EntityNotFoundException;
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
