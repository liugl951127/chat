package com.fin.commons.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文
 *
 * - 通过 filter 写入 MDC
 * - logback 配置里用 %X{traceId} 打印
 * - 响应头 X-Trace-Id 透传给上游
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceContext() {}

    public static String get() {
        String tid = MDC.get(MDC_KEY);
        return tid != null ? tid : "no-trace";
    }

    public static String getOrCreate() {
        String tid = MDC.get(MDC_KEY);
        if (tid == null) {
            tid = UUID.randomUUID().toString().replace("-", "");
            MDC.put(MDC_KEY, tid);
        }
        return tid;
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
