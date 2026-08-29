package com.lobmatrix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LobMatrixApplication {

    private static final Logger log = LoggerFactory.getLogger(LobMatrixApplication.class);

    public static void main(String[] args) {
        log.info("Starting LobMatrix High-Performance Microstructure Engine...");
        SpringApplication.run(LobMatrixApplication.class, args);
        log.info("LobMatrix Engine initialized successfully on Java 21 runtime.");
    }
}
