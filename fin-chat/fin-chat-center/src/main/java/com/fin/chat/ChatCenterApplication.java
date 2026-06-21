package com.fin.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
        "com.fin.chat",
        "com.fin.commons"
})
@EnableAsync
public class ChatCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatCenterApplication.class, args);
    }
}
