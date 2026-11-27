package com.finny.api.application.service;

import com.finny.api.application.dto.response.AvatarResponse;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.exception.PayloadTooLargeException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.application.mapper.AvatarUrls;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.entity.UserAvatar;
import com.finny.api.domain.repository.UserAvatarRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final AvatarImageProcessor imageProcessor;
    private final Clock clock;

    public record AvatarContent(byte[] content, String contentType, String version) {}

    @Transactional
    public AvatarResponse upload(UUID userId, MultipartFile file) {
        AvatarImageProcessor.ProcessedImage image = imageProcessor.process(readLimited(file));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        UserAvatar avatar = avatarRepository.findById(userId).orElseGet(() -> {
            UserAvatar a = new UserAvatar();
            a.setUserId(userId);
            return a;
        });
        avatar.setContent(image.content());
        avatar.setContentType(image.contentType());
        avatar.setUpdatedAt(OffsetDateTime.now(clock));
        avatarRepository.save(avatar);

        user.setAvatarVersion(version(image.content()));
        userRepository.save(user);

        log.info("Foto de perfil atualizada | id: {} | bytes: {}", userId, image.content().length);
        return new AvatarResponse(AvatarUrls.forVersion(user.getAvatarVersion()));
    }

    @Transactional(readOnly = true)
    public AvatarContent get(UUID userId) {
        UserAvatar avatar = avatarRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário sem foto de perfil"));
        return new AvatarContent(avatar.getContent(), avatar.getContentType(), version(avatar.getContent()));
    }

    @Transactional
    public void delete(UUID userId) {
        avatarRepository.findById(userId).ifPresent(avatarRepository::delete);
        userRepository.findById(userId).ifPresent(user -> {
            user.setAvatarVersion(null);
            userRepository.save(user);
        });
        log.info("Foto de perfil removida | id: {}", userId);
    }

    /** Lê no máximo o limite + 1 byte: não confia no tamanho declarado pelo cliente. */
    private static byte[] readLimited(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Envie uma imagem no campo \"file\"");
        }
        if (file.getSize() > AvatarImageProcessor.MAX_UPLOAD_BYTES) {
            throw new PayloadTooLargeException("A foto deve ter no máximo 2 MB");
        }
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes((int) AvatarImageProcessor.MAX_UPLOAD_BYTES + 1);
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler a imagem");
        }
    }

    static String version(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
