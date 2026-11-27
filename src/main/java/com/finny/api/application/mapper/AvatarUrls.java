package com.finny.api.application.mapper;

/** Monta o caminho relativo da foto de perfil. O parâmetro v muda a cada upload (invalida cache). */
public final class AvatarUrls {

    public static final String PATH = "/api/v1/users/me/avatar";

    private AvatarUrls() {}

    public static String forVersion(String version) {
        return version == null || version.isBlank() ? null : PATH + "?v=" + version;
    }
}
