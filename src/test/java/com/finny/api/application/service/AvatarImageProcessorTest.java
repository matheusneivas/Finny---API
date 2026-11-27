package com.finny.api.application.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarImageProcessorTest {

    @Test
    void detectsFormatsByMagicBytes() {
        assertThat(AvatarImageProcessor.detectFormat(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("jpeg");
        assertThat(AvatarImageProcessor.detectFormat(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0))).isEqualTo("png");
        assertThat(AvatarImageProcessor.detectFormat("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("webp");
        assertThat(AvatarImageProcessor.detectFormat("GIF89a......".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(AvatarImageProcessor.detectFormat("<svg xmlns=''>".getBytes(StandardCharsets.ISO_8859_1))).isNull();
    }

    @Test
    void webpReaderIsAvailable() {
        assertThat(javax.imageio.ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }
}
