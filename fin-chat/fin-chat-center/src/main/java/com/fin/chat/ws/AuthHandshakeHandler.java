package com.fin.chat.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 从 handshake attributes 取出 userId, 包装成 Principal
 *
 * <p>Spring 的 STOMP /user/queue/... 路由依靠 Principal, 必须这一步.
 *
 * @author Mavis
 */
@Slf4j
@Component
public class AuthHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        Object uid = attributes.get("userId");
        if (uid == null) {
            // 兼容测试: 从 token 解 (demo.token.{base64}.demo)
            Object token = attributes.get("token");
            if (token instanceof String t && t.startsWith("demo.")) {
                try {
                    String[] parts = t.split("\\.");
                    String json = new String(java.util.Base64.getDecoder().decode(parts[1]));
                    int idx = json.indexOf("\"sub\":");
                    if (idx >= 0) {
                        int start = json.indexOf('"', idx + 6) + 1;
                        int end = json.indexOf('"', start);
                        uid = json.substring(start, end);
                    }
                } catch (Exception ignored) {}
            }
        }
        if (uid == null) {
            log.warn("[WS HANDSHAKE] no userId, anonymous");
            return null;
        }
        final String userId = uid.toString();
        log.info("[WS HANDSHAKE] userId={} bound as Principal", userId);
        return () -> userId;
    }
}