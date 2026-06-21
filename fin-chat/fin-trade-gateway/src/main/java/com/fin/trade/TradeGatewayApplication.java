package com.fin.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
        "com.fin.trade",
        "com.fin.commons"
})
@EnableAsync
public class TradeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeGatewayApplication.class, args);
    }
}
