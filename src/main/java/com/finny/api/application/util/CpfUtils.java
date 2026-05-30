package com.finny.api.application.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class CpfUtils {

    private CpfUtils() {}

    public static String sanitize(String cpf) {
        return cpf.replaceAll("[.\\-\\s]", "");
    }

    public static boolean isValid(String cpf) {
        String digits = sanitize(cpf);

        if (digits.length() != 11) return false;

        // Rejeita sequências inválidas como 000.000.000-00, 111.111.111-11, etc.
        if (digits.chars().distinct().count() == 1) return false;

        int[] d = digits.chars().map(c -> c - '0').toArray();

        // Primeiro dígito verificador
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += d[i] * (10 - i);
        int v1 = 11 - (sum % 11);
        if (v1 >= 10) v1 = 0;
        if (v1 != d[9]) return false;

        // Segundo dígito verificador
        sum = 0;
        for (int i = 0; i < 10; i++) sum += d[i] * (11 - i);
        int v2 = 11 - (sum % 11);
        if (v2 >= 10) v2 = 0;
        return v2 == d[10];
    }

    public static String hash(String cpf, String secret) {
        try {
            String digits = sanitize(cpf);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(key);
            byte[] result = mac.doFinal(digits.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar CPF", e);
        }
    }
}
