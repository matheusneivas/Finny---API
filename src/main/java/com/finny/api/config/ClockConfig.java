package com.finny.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Relógio injetável (facilita testes de regras por data). */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
