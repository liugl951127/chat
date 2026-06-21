package com.fin.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.fin.compliance",
        "com.fin.commons"
})
public class ComplianceCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComplianceCenterApplication.class, args);
    }
}
