package com.fin.chat.ws;

import com.fin.commons.user.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权
 *
 * <p>从 query string 取 token, 简单校验格式 (生产验签)
 */
@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. 优先从 query 取
        String token = extractQueryToken(request);
        // 2. fallback 从 header 取
        if (token == null && request instanceof ServletServerHttpRequest sreq) {
            HttpServletRequest http = sreq.getServletRequest();
            token = http.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        // 3. 从 X-User-Id 取 (网关已鉴权)
        String userId = null;
        if (request instanceof ServletServerHttpRequest sreq) {
            userId = sreq.getServletRequest().getHeader("X-User-Id");
        }

        if (userId == null && (token == null || token.isEmpty())) {
            log.warn("WS handshake 拒绝: 无 token 无 userId");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (userId != null) {
            attributes.put("userId", Long.parseLong(userId));
        }
        if (token != null) {
            attributes.put("token", token);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractQueryToken(ServerHttpRequest req) {
        String query = req.getURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
