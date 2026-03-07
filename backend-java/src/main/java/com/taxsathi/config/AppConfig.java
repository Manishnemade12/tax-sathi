package com.taxsathi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Application-wide bean configuration.
 * Provides shared HttpClient and ObjectMapper instances used by services.
 */
@Configuration
public class AppConfig {

    /**
     * Java 11+ HttpClient — reusable connection pool, configured with a 30s connect timeout.
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Jackson ObjectMapper:
     * - Ignores unknown properties (safe for evolving Supabase schemas)
     * - Does NOT fail on null values
     * - Uses snake_case for JSON ↔ Java field mapping
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
