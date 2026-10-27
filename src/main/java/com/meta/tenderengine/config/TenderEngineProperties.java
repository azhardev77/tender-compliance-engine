package com.meta.tenderengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tender.engine")
public record TenderEngineProperties(
        int pdfWorkerThreads,
        int rasterMaxConcurrentPages,
        int rasterDpi,
        int standardsVersion,
        String promptVersion) {
}
