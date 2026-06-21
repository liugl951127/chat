package com.fin.chat.controller;

import com.fin.chat.dto.ChatMessage;
import com.fin.chat.dto.Conversation;
import com.fin.chat.service.ConversationService;
import com.fin.chat.service.HashChainService;
import com.fin.commons.resp.ApiResponse;
import com.fin.commons.user.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 REST API
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ConversationService conversationService;
    private final HashChainService hashChainService;

    @GetMapping("/conversations")
    public ApiResponse<List<Conversation>> listConversations() {
        Long userId = UserContext.getUserId();
        if (userId == null) return ApiResponse.ok(List.of());
        return ApiResponse.ok(conversationService.listByUser(userId));
    }

    @PostMapping("/conversations")
    public ApiResponse<Conversation> createConversation(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");
        Long advisorId = body.get("advisorId") == null ? null : Long.valueOf(body.get("advisorId").toString());
        String type = (String) body.getOrDefault("type", "P2P");
        String title = (String) body.get("title");
        return ApiResponse.ok(conversationService.create(userId, advisorId, type, title));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<Conversation> getConversation(@PathVariable String id) {
        Conversation c = conversationService.get(id);
        if (c == null) throw new IllegalStateException("会话不存在");
        return ApiResponse.ok(c);
    }

    @PostMapping("/messages")
    public ApiResponse<ChatMessage> sendMessage(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");

        String conversationId = (String) body.get("conversationId");
        String content = (String) body.get("content");
        String type = (String) body.getOrDefault("type", "TEXT");
        if (conversationId == null || content == null) {
            throw new IllegalArgumentException("conversationId/content 必填");
        }

        Conversation conv = conversationService.get(conversationId);
        if (conv == null) throw new IllegalStateException("会话不存在");

        // 1. 哈希链
        String prev = hashChainService.getLastHash(conversationId);
        String hash = hashChainService.nextHash(conversationId, content, prev);

        // 2. 消息组装
        ChatMessage msg = ChatMessage.builder()
                .msgId(com.fin.commons.util.IdGenerator.msgId())
                .conversationId(conversationId)
                .senderId(userId)
                .senderType("CUSTOMER")
                .type(type)
                .content(content)
                .contentHash(hash)
                .prevHash(prev)
                .serverTs(System.currentTimeMillis())
                .build();

        // 3. 更新会话
        conversationService.touch(conversationId, msg.getMsgId());

        log.info("[CHAT] msg sent: conv={}, user={}, len={}", conversationId, userId, content.length());
        return ApiResponse.ok(msg);
    }

    @GetMapping("/messages/{conversationId}")
    public ApiResponse<Map<String, Object>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "20") int size) {
        // 沙箱: 返回占位 (生产接 MongoDB)
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of());
        result.put("total", 0);
        result.put("conversationId", conversationId);
        return ApiResponse.ok(result);
    }

    @GetMapping("/verify/{conversationId}")
    public ApiResponse<Map<String, Object>> verifyChain(@PathVariable String conversationId) {
        // 沙箱: 简化, 返回最后 hash
        Map<String, Object> report = new HashMap<>();
        report.put("conversationId", conversationId);
        report.put("lastHash", hashChainService.getLastHash(conversationId));
        report.put("status", "OK");
        report.put("ts", LocalDateTime.now().toString());
        return ApiResponse.ok(report);
    }
}
