package com.fin.kms.config;

import com.fin.kms.KmsGatewayClient;
import com.fin.kms.hsm.HsmKmsClient;
import com.fin.kms.soft.SoftKmsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * KMS 客户端装配
 *
 * <p>软算法 fallback: dev/sandbox 用, 生产禁用
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KmsProperties.class)
public class KmsAutoConfig {

    /** 软算法 (沙箱) */
    @Bean
    @ConditionalOnProperty(name = "fin.kms.soft-fallback.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public KmsGatewayClient softKmsClient() {
        log.warn("!! SoftKmsClient active, this is for DEV/SANDBOX only !!");
        return new SoftKmsClient();
    }

    /** 硬件加密机 (生产) */
    @Bean
    @ConditionalOnProperty(name = "fin.kms.soft-fallback.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean
    public KmsGatewayClient hsmKmsClient(KmsProperties props) {
        log.info("HsmKmsClient init: host={}, port={}", props.getHost(), props.getPort());
        return new HsmKmsClient(
                props.getHost(),
                props.getPort(),
                props.getAppId(),
                props.getAppKey(),
                props.getPoolSize());
    }
}
