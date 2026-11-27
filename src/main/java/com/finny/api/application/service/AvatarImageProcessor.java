package com.finny.api.application.service;

import com.finny.api.application.exception.PayloadTooLargeException;
import com.finny.api.application.exception.ValidationException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Valida e re-encoda a foto de perfil.
 * <p>
 * O formato é decidido pela assinatura do arquivo (magic bytes), nunca pelo Content-Type. As dimensões
 * são conferidas antes de decodificar (proteção contra "decompression bomb"). A imagem é redesenhada
 * do zero e salva como JPEG, o que descarta EXIF/GPS, perfis e qualquer conteúdo escondido no arquivo.
 */
@Component
public class AvatarImageProcessor {

    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    static final int OUTPUT_MAX_SIDE = 512;
    static final int MAX_INPUT_SIDE = 6000;
    private static final float JPEG_QUALITY = 0.85f;

    public record ProcessedImage(byte[] content, String contentType) {}

    public ProcessedImage process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new ValidationException("Envie uma imagem");
        }
        if (input.length > MAX_UPLOAD_BYTES) {
            throw new PayloadTooLargeException("A foto deve ter no máximo 2 MB");
        }
        String format = detectFormat(input);
        if (format == null) {
            throw new ValidationException("Formato não suportado. Use JPEG, PNG ou WEBP");
        }
        BufferedImage source = decode(input, format);
        return new ProcessedImage(encodeJpeg(resize(source)), "image/jpeg");
    }

    static String detectFormat(byte[] h) {
        if (h.length < 12) return null;
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpeg";
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
            && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return "png";
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
            && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }

    private static BufferedImage decode(byte[] input, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new ValidationException("Formato não suportado. Use JPEG, PNG ou WEBP");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || width > MAX_INPUT_SIDE || height > MAX_INPUT_SIDE) {
                throw new ValidationException("Imagem com dimensões inválidas (máximo " + MAX_INPUT_SIDE + " px)");
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new ValidationException("Arquivo não parece ser uma imagem válida");
            }
            return image;
        } catch (ValidationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ValidationException("Arquivo não parece ser uma imagem válida");
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage resize(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) OUTPUT_MAX_SIDE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        BufferedImage target = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // fundo para PNG/WEBP com transparência
            g.fillRect(0, 0, tw, th);
            g.drawImage(source, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a imagem", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
