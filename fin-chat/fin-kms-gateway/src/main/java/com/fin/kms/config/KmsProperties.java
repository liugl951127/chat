package com.fin.kms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fin.kms")
public class KmsProperties {

    private String host = "127.0.0.1";
    private int port = 8000;
    private String appId = "fin-app-001";
    private String appKey;
    private int poolSize = 8;
    private int timeoutMs = 3000;

    private SoftFallback softFallback = new SoftFallback();

    @Data
    public static class SoftFallback {
        /** ⚠️ 仅 dev/sandbox 用, 生产必须 false */
        private boolean enabled = false;
    }
}
