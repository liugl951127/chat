package com.fin.commons.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 注入 (Servlet)
 *
 * - 优先取上游 X-Trace-Id
 * - 没有则生成 UUID (no -)
 * - 写入 MDC + 响应头
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String traceId = req.getHeader(TraceContext.HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = java.util.UUID.randomUUID().toString().replace("-", "");
            }
            TraceContext.set(traceId);
            resp.setHeader(TraceContext.HEADER, traceId);
            chain.doFilter(req, resp);
        } finally {
            TraceContext.clear();
        }
    }
}
