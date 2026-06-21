package com.fin.kms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * KMS 网关启动器
 */
@SpringBootApplication(scanBasePackages = {
        "com.fin.kms",
        "com.fin.commons"
})
public class KmsGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(KmsGatewayApplication.class, args);
    }
}
