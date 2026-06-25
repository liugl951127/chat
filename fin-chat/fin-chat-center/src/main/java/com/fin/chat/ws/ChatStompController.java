package com.fin.chat.ws;

import com.fin.chat.dto.ChatMessage;
import com.fin.chat.service.ConversationService;
import com.fin.chat.service.HashChainService;
import com.fin.commons.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 消息入口
 *
 * <p>前端: SEND /app/chat/{conversationId}
 * <p>服务端: 落库 + 哈希链 + 广播到会话主题
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ConversationService conversationService;
    private final HashChainService hashChainService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{conversationId}")
    public void onMessage(@DestinationVariable String conversationId,
                          Principal principal,
                          ChatMessage msg) {
        // 1. 拿 userId (HandshakeHandler 已绑 Principal)
        Long userId = principal == null ? null : Long.parseLong(principal.getName());
        if (userId == null) {
            log.warn("WS message 无 userId");
            return;
        }

        // 2. 哈希链
        String prev = hashChainService.getLastHash(conversationId);
        String hash = hashChainService.nextHash(conversationId, msg.getContent(), prev);

        // 3. 装消息
        msg.setSenderId(userId);
        msg.setSenderType("CUSTOMER");
        msg.setMsgId(IdGenerator.msgId());
        msg.setServerTs(System.currentTimeMillis());
        msg.setContentHash(hash);
        msg.setPrevHash(prev);
        msg.setConversationId(conversationId);

        // 4. 更新会话
        conversationService.touch(conversationId, msg.getMsgId());

        // 5. 广播到 /topic/chat/{convId} - 所有订阅该会话的人收到
        messagingTemplate.convertAndSend("/topic/chat/" + conversationId, msg);

        log.info("[WS] msg sent: conv={}, user={}, len={}", conversationId, userId, msg.getContent().length());
    }
}
