package com.finny.api.application.exception;

/** Arquivo acima do limite da rota (HTTP 413). */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
