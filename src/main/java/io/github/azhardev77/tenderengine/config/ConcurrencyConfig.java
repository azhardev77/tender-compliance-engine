package io.github.azhardev77.tenderengine.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConcurrencyConfig {

    @Bean(name = "pdfExecutor", destroyMethod = "shutdown")
    ExecutorService pdfExecutor(TenderEngineProperties props) {
        return Executors.newFixedThreadPool(props.pdfWorkerThreads());
    }
}
