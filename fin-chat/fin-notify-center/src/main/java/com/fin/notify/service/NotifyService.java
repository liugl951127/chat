package com.fin.notify.service;

import com.fin.notify.dto.SendSmsRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通知服务 (沙箱: log, 生产: 调阿里云/腾讯云/华为云)
 */
@Slf4j
@Service
public class NotifyService {

    private final AtomicLong counter = new AtomicLong();
    private final Map<String, Long> sentLog = new ConcurrentHashMap<>();

    public SendResult sendSms(SendSmsRequest req) {
        String traceId = "SMS-" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        log.info("📨 [SMS] to={}, biz={}, tpl={}, content={}",
                maskMobile(req.getMobile()), req.getBiz(),
                req.getTemplateCode(),
                req.getContent().length() > 50 ? req.getContent().substring(0, 50) + "..." : req.getContent());
        sentLog.put(traceId, System.currentTimeMillis());
        return new SendResult(true, traceId, "OK");
    }

    public long countSent() {
        return sentLog.size();
    }

    private String maskMobile(String m) {
        if (m == null || m.length() < 7) return m;
        return m.substring(0, 3) + "****" + m.substring(7);
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendResult {
        private boolean success;
        private String traceId;
        private String message;
    }
}
