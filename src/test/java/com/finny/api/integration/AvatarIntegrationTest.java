package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvatarIntegrationTest extends IntegrationTestBase {

    private static final String PATH = "/api/v1/users/me/avatar";

    @Test
    void uploadResizesStripsMetadataAndAppearsOnLogin() throws Exception {
        Session session = register();
        byte[] jpegWithExif = injectExif(image("jpg", 1200, 900));

        MvcResult upload = mockMvc.perform(put(session, file("foto.jpg", "image/jpeg", jpegWithExif)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith(PATH + "?v=")))
            .andReturn();
        String avatarUrl = objectMapper.readTree(upload.getResponse().getContentAsString()).get("avatarUrl").asText();

        MvcResult download = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("private")))
            .andReturn();
        byte[] stored = download.getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(img.getWidth()).isEqualTo(512);
        assertThat(img.getHeight()).isEqualTo(384);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("SEGREDO-GPS");

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json(Map.of("email", session.email(), "password", "Senha123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.avatarUrl").value(avatarUrl));
    }

    @Test
    void pngWithTransparencyIsAccepted() throws Exception {
        Session session = register();
        mockMvc.perform(put(session, file("foto.png", "image/png", image("png", 300, 300))))
            .andExpect(status().isOk());
    }

    @Test
    void newUserHasNoAvatar() throws Exception {
        Session session = register();
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsFakeImageEvenWithImageContentType() throws Exception {
        Session session = register();
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(put(session, file("foto.jpg", "image/jpeg", html)))
            .andExpect(status().isBadRequest());

        // Assinatura de JPEG válida, mas conteúdo corrompido
        byte[] broken = new byte[64];
        broken[0] = (byte) 0xFF; broken[1] = (byte) 0xD8; broken[2] = (byte) 0xFF;
        mockMvc.perform(put(session, file("foto.jpg", "image/jpeg", broken)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFilesOver2Mb() throws Exception {
        Session session = register();
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        mockMvc.perform(put(session, file("foto.jpg", "image/jpeg", big)))
            .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void deleteRemovesAvatar() throws Exception {
        Session session = register();
        mockMvc.perform(put(session, file("foto.jpg", "image/jpeg", image("jpg", 100, 100))))
            .andExpect(status().isOk());

        mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(session)))
            .andExpect(status().isNotFound());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json(Map.of("email", session.email(), "password", "Senha123"))))
            .andExpect(status().isOk()).andReturn();
        JsonNode user = objectMapper.readTree(login.getResponse().getContentAsString()).get("user");
        assertThat(user.get("avatarUrl").isNull()).isTrue();
    }

    @Test
    void eachUserOnlySeesOwnAvatar() throws Exception {
        Session owner = register();
        Session other = register();
        mockMvc.perform(put(owner, file("foto.jpg", "image/jpeg", image("jpg", 100, 100))))
            .andExpect(status().isOk());
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(other)))
            .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    private MockMultipartHttpServletRequestBuilder put(Session session, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = multipart(HttpMethod.PUT, PATH).file(file);
        builder.header(HttpHeaders.AUTHORIZATION, bearer(session));
        return builder;
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    static byte[] image(String format, int width, int height) throws Exception {
        boolean alpha = format.equals("png");
        BufferedImage img = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 140, 90, alpha ? 128 : 255));
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** Insere um segmento APP1 "Exif" logo após o SOI, simulando foto de celular com GPS. */
    private static byte[] injectExif(byte[] jpeg) {
        byte[] payload = "Exif\0\0SEGREDO-GPS-23.5505S-46.6333W".getBytes(StandardCharsets.ISO_8859_1);
        int len = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF); out.write(0xE1); out.write(len >> 8); out.write(len & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }
}
