package com.eems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EemsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EemsApplication.class, args);
    }
}
