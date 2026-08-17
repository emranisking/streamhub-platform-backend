package com.streamhub.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StreamHub Platform.
 * <p>
 * A monolithic Spring Boot rewrite of the original NestJS video streaming
 * backend. See /docs/README.md for module layout, and
 * /docs/API_DOCUMENTATION.md + /docs/API_STORYLINE.md for the full endpoint
 * reference.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class StreamhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamhubApplication.class, args);
    }
}
