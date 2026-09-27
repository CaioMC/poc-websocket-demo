package com.example.wschat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ver README.md e docs/ para arquitetura, protocolo e como rodar. */
@SpringBootApplication
public class PocWebSocketDemoApplication {

    private static final Logger logger = LoggerFactory.getLogger(PocWebSocketDemoApplication.class);

    public static void main(String[] args) {
        logger.info("ola");
        SpringApplication.run(PocWebSocketDemoApplication.class, args);
    }
}