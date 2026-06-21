package com.fin.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String traceId;
    private Long userId;
    private String userRole;
    private String eventType;     // LOGIN / TRADE / SMS / KMS / ...
    private String targetType;
    private String targetId;
    private String action;
    private Map<String, Object> payload;
    private String result;        // SUCCESS / FAIL
    private String ip;
    private String deviceId;
    private Long costMs;
    private LocalDateTime ts;
    private String prevHash;
    private String currHash;
}
