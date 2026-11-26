package com.finny.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {

    @NotBlank(message = "Token é obrigatório")
    @Size(max = 4096, message = "Token inválido")
    private String token;

    @Pattern(regexp = "IOS|ANDROID|WEB", message = "Plataforma deve ser IOS, ANDROID ou WEB")
    private String platform = "WEB";
}
