package com.fin.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.fin.notify",
        "com.fin.commons"
})
public class NotifyCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotifyCenterApplication.class, args);
    }
}
