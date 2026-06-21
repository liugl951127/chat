package com.fin.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
        "com.fin.audit",
        "com.fin.commons"
})
@EnableAsync
public class AuditCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditCenterApplication.class, args);
    }
}
