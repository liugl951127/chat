package com.fin.audit.controller;

import com.fin.audit.dto.AuditEvent;
import com.fin.audit.service.AuditService;
import com.fin.commons.resp.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计 Controller
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** 记录事件 */
    @PostMapping("/record")
    public ApiResponse<AuditEvent> record(@RequestBody AuditEvent event) {
        return ApiResponse.ok(auditService.record(event));
    }

    /** 按 traceId 查询 */
    @GetMapping("/query/trace/{traceId}")
    public ApiResponse<List<AuditEvent>> queryByTrace(@PathVariable String traceId) {
        return ApiResponse.ok(auditService.queryByTraceId(traceId));
    }

    /** 按 userId 查询 */
    @GetMapping("/query/user/{userId}")
    public ApiResponse<List<AuditEvent>> queryByUser(@PathVariable Long userId,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(auditService.queryByUser(userId, limit));
    }

    /** 按事件类型查询 */
    @GetMapping("/query/event/{eventType}")
    public ApiResponse<List<AuditEvent>> queryByEvent(@PathVariable String eventType,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(auditService.queryByEventType(eventType, limit));
    }

    /** 哈希链验证 */
    @GetMapping("/verify/chain")
    public ApiResponse<AuditService.VerifyReport> verifyChain() {
        return ApiResponse.ok(auditService.verifyChain());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("totalEvents", auditService.count());
        return ApiResponse.ok(s);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "module", "fin-audit-center"));
    }
}
