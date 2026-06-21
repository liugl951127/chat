package com.fin.observability.controller;

import com.fin.commons.resp.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 集中健康检查 — 检查所有下游模块
 */
@RestController
@RequestMapping("/api/v1/observability")
@Slf4j
public class HealthController {

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Value("${fin.modules.auth-url:http://localhost:8081}")
    private String authUrl;
    @Value("${fin.modules.chat-url:http://localhost:8082}")
    private String chatUrl;
    @Value("${fin.modules.trade-url:http://localhost:8083}")
    private String tradeUrl;
    @Value("${fin.modules.compliance-url:http://localhost:8090}")
    private String complianceUrl;
    @Value("${fin.modules.kms-url:http://localhost:8091}")
    private String kmsUrl;
    @Value("${fin.modules.notify-url:http://localhost:8092}")
    private String notifyUrl;
    @Value("${fin.modules.audit-url:http://localhost:8093}")
    private String auditUrl;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("ts", LocalDateTime.now().toString());

        Map<String, String> modules = new HashMap<>();
        modules.put("auth", checkUrl(authUrl + "/api/v1/auth/health"));
        modules.put("chat", checkUrl(chatUrl + "/api/v1/chat/health"));
        modules.put("trade", checkUrl(tradeUrl + "/api/v1/trade/health"));
        modules.put("compliance", checkUrl(complianceUrl + "/api/v1/compliance/health"));
        modules.put("kms", checkUrl(kmsUrl + "/api/kms/health"));
        modules.put("notify", checkUrl(notifyUrl + "/api/v1/notify/health"));
        modules.put("audit", checkUrl(auditUrl + "/api/v1/audit/health"));
        result.put("modules", modules);

        return ApiResponse.ok(result);
    }

    private String checkUrl(String url) {
        if (restTemplate == null) return "UNKNOWN";
        try {
            // 沙箱: 简化为 1s 超时
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Map resp = restTemplate.getForObject(url, Map.class);
                    return resp == null ? "DOWN" : "UP";
                } catch (Exception e) {
                    return "DOWN";
                }
            }).get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "DOWN";
        }
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        Map<String, Object> m = new HashMap<>();
        m.put("jvm", "UP");
        m.put("ts", LocalDateTime.now().toString());
        return ApiResponse.ok(m);
    }
}
