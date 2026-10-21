package com.sanedge.pointofsale.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "com.sanedge.pointofsale")
@EntityScan({ "com.sanedge.pointofsale.order.models", "com.sanedge.pointofsale.common.outbox" })
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
