package com.fin.chat.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Base64;
import java.util.Map;

/**
 * STOMP 连接级拦截: 鉴权 + 用户绑定
 *
 * <p>从 CONNECT header 提取 userId (X-User-Id 或 token), 设到 Principal,
 * <p>这样 /user/queue/chat 路由才能找到对应用户
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 1. 优先 X-User-Id
            String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
            // 2. fallback: 从 token 解 (demo.token.{base64-json}.demo)
            if (userIdHeader == null) {
                String token = accessor.getFirstNativeHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    if (token.startsWith("demo.")) {
                        try {
                            String[] parts = token.split("\\.");
                            String json = new String(Base64.getDecoder().decode(parts[1]));
                            // 简单解析 "sub"
                            int idx = json.indexOf("\"sub\":");
                            if (idx >= 0) {
                                int start = json.indexOf('"', idx + 6) + 1;
                                int end = json.indexOf('"', start);
                                userIdHeader = json.substring(start, end);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (userIdHeader != null) {
                final String uid = userIdHeader;
                Principal principal = new Principal() {
                    @Override public String getName() { return uid; }
                };
                accessor.setUser(principal);
                log.info("[WS CONNECT] userId={} bound to Principal, accessor.getUser()={}", uid, accessor.getUser());
            } else {
                log.warn("[WS CONNECT] no userId found, anonymous");
            }
        }
        return message;
    }
}