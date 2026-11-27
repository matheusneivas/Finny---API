package com.finny.api.application.util;

/** Telefone brasileiro: guarda só os dígitos com DDD (10 ou 11 dígitos), sem o +55. */
public final class PhoneUtils {

    private PhoneUtils() {}

    /** @return dígitos com DDD, ou null se não for um telefone válido */
    public static String normalize(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10 && digits.length() != 11) return null;
        if (digits.charAt(0) == '0' || digits.charAt(1) == '0') return null; // DDD inválido
        if (digits.length() == 11 && digits.charAt(2) != '9') return null; // celular começa com 9
        return digits;
    }
}
