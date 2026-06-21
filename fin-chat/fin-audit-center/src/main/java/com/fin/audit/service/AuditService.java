package com.fin.audit.service;

import com.fin.audit.dto.AuditEvent;
import com.fin.commons.util.IdGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 审计服务 (沙箱内存版, 生产接 MySQL)
 *
 * <p>所有事件入链 (SHA-256), 形成不可篡改序列
 * <p>支持按用户/事件/时间查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final List<AuditEvent> store = new ArrayList<>();
    private final Map<String, Long> indexByTraceId = new ConcurrentHashMap<>();
    private volatile String lastHash = "GENESIS";

    /** 记录事件 (含哈希链) */
    public synchronized AuditEvent record(AuditEvent event) {
        if (event.getTs() == null) event.setTs(LocalDateTime.now());
        if (event.getTraceId() == null) event.setTraceId("T-" + IdGenerator.uuid());

        // 计算 hash
        String prev = lastHash;
        String curr = computeHash(event, prev);
        event.setPrevHash(prev);
        event.setCurrHash(curr);

        store.add(event);
        indexByTraceId.put(event.getTraceId(), (long) store.size() - 1);
        lastHash = curr;

        log.debug("[AUDIT] {} hash={}", event.getEventType(), curr.substring(0, 8));
        return event;
    }

    /** 按 traceId 查询 */
    public List<AuditEvent> queryByTraceId(String traceId) {
        return store.stream()
                .filter(e -> traceId.equals(e.getTraceId()))
                .collect(Collectors.toList());
    }

    /** 按用户查询 */
    public List<AuditEvent> queryByUser(Long userId, int limit) {
        return store.stream()
                .filter(e -> userId != null && userId.equals(e.getUserId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 按事件类型查询 */
    public List<AuditEvent> queryByEventType(String eventType, int limit) {
        return store.stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 哈希链验证 */
    public VerifyReport verifyChain() {
        String expected = "GENESIS";
        int ok = 0, fail = 0;
        for (AuditEvent e : store) {
            String recomputed = computeHash(e, e.getPrevHash());
            if (recomputed.equals(e.getCurrHash())) {
                ok++;
            } else {
                fail++;
                log.error("哈希链断裂: traceId={}, expected={}, got={}",
                        e.getTraceId(), e.getCurrHash(), recomputed);
            }
        }
        return new VerifyReport(ok, fail, store.size());
    }

    public long count() {
        return store.size();
    }

    private String computeHash(AuditEvent e, String prev) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = (prev == null ? "GENESIS" : prev) + "|"
                    + e.getTraceId() + "|"
                    + e.getEventType() + "|"
                    + e.getUserId() + "|"
                    + e.getResult() + "|"
                    + e.getTs();
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class VerifyReport {
        private int okCount;
        private int failCount;
        private int totalCount;
    }
}
