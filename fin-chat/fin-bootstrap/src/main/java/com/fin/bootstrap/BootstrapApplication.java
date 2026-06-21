package com.fin.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一启动器 (单体模式, 沙箱演示用)
 *
 * <p>生产: 各模块独立部署; 沙箱: 启动这一个包含所有模块
 */
@SpringBootApplication(scanBasePackages = {
        "com.fin"
})
public class BootstrapApplication {
    public static void main(String[] args) {
        // 沙箱: 设置端口避免冲突
        System.setProperty("server.port", "8080");
        SpringApplication.run(BootstrapApplication.class, args);
    }
}
