package com.fin.chat.ws;

import com.fin.chat.dto.ChatMessage;
import com.fin.chat.service.ConversationService;
import com.fin.chat.service.HashChainService;
import com.fin.commons.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 消息入口
 *
 * <p>前端: SEND /app/chat/{conversationId}
 * <p>服务端: 落库 + 哈希链 + 推给对方
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ConversationService conversationService;
    private final HashChainService hashChainService;

    @MessageMapping("/chat/{conversationId}")
    @SendToUser("/queue/chat")
    public ChatMessage onMessage(@DestinationVariable String conversationId,
                                   Principal principal,
                                   ChatMessage msg) {
        // 1. 拿 userId
        Long userId = principal == null ? null : Long.parseLong(principal.getName());
        if (userId == null) {
            log.warn("WS message 无 userId");
            return null;
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

        log.info("[WS] msg sent: conv={}, user={}, len={}", conversationId, userId, msg.getContent().length());
        return msg;
    }
}
