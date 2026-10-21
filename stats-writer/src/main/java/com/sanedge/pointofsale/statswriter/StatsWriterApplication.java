package com.sanedge.pointofsale.statswriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StatsWriterApplication {

    public static void main(String[] args) {
        // One-shot backfill mode: web stack disabled, run the CommandLineRunner then exit.
        if (Boolean.parseBoolean(System.getenv().getOrDefault("APP_BACKFILL_ENABLED", "false"))) {
            System.setProperty("spring.main.web-application-type", "none");
        }
        SpringApplication.run(StatsWriterApplication.class, args);
    }
}
