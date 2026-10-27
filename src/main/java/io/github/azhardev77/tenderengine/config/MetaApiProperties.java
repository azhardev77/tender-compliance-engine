package io.github.azhardev77.tenderengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tender.ai")
public record MetaApiProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String museSparkModel,
        String samModel,
        int connectTimeoutMs,
        int readTimeoutMs) {

    public boolean isMock() {
        return "mock".equalsIgnoreCase(provider);
    }
}
