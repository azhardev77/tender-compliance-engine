package com.meta.tenderengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TenderEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenderEngineApplication.class, args);
    }
}
