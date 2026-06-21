package com.fin.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Auth Center 启动器
 */
@SpringBootApplication(scanBasePackages = {
        "com.fin.auth",
        "com.fin.commons"
})
@EnableAsync
@MapperScan("com.fin.auth.mapper")
public class AuthCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthCenterApplication.class, args);
    }
}
