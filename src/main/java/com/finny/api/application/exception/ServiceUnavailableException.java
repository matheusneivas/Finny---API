package com.finny.api.application.exception;

/** Dependência externa indisponível (ex.: provedor de IA). Vira HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
