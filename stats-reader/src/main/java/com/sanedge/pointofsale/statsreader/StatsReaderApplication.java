package com.sanedge.pointofsale.statsreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sanedge.pointofsale")
public class StatsReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatsReaderApplication.class, args);
    }
}
