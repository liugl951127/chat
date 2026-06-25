package com.fin.chat.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket 配置
 *
 * <p>端点: /ws/chat (SockJS 兼容)
 * <p>目的地: /app/chat/** (服务端接收) / /user/queue/** (点对点) / /topic/** (广播)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final AuthHandshakeHandler handshakeHandler;
    private final AuthHandshakeInterceptor handshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue/", "/topic/")
              .setHeartbeatValue(new long[]{10000, 10000})
              .setTaskScheduler(heartBeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user/");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 只注册原生 WebSocket (不要 SockJS, SockJS 会把消息包成 a["..."] 数组格式)
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor);
    }

    private TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("ws-heartbeat-");
        s.initialize();
        return s;
    }
}
