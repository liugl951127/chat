package com.fin.chat.controller;

import com.fin.chat.dto.ChatMessage;
import com.fin.chat.dto.Conversation;
import com.fin.chat.service.ConversationService;
import com.fin.chat.service.MessageService;
import com.fin.commons.resp.ApiResponse;
import com.fin.commons.user.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天 REST API
 *
 * <p>前端: /api/v1/chat/...
 * <p>路径: 后端直连 8082 (生产经 Gateway 9000)
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "fin-chat-center");
        status.put("status", "UP");
        status.put("ts", System.currentTimeMillis());
        return ApiResponse.ok(status);
    }

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

    /**
     * 客服 (agent) 主动发起会话
     * POST /api/v1/chat/agent/conversations
     * body: {customerId, title?}
     */
    @PostMapping("/agent/conversations")
    public ApiResponse<Conversation> agentCreateConversation(@RequestBody Map<String, Object> body) {
        Long agentId = UserContext.getUserId();
        if (agentId == null) throw new IllegalStateException("未登录");
        if (agentId.toString().startsWith("8")) {
            // mock: agent id 以 8 开头代表客服, 允许发起
        }
        Long customerId = body.get("customerId") == null ? null
                : Long.valueOf(body.get("customerId").toString());
        if (customerId == null) throw new IllegalArgumentException("customerId 必填");
        String title = (String) body.get("title");
        return ApiResponse.ok(conversationService.agentInitiate(agentId, customerId, title));
    }

    /**
     * 客服查看: 我接的客户会话
     * GET /api/v1/chat/agent/conversations
     */
    @GetMapping("/agent/conversations")
    public ApiResponse<java.util.List<Conversation>> listAgentConversations() {
        Long agentId = UserContext.getUserId();
        if (agentId == null) throw new IllegalStateException("未登录");
        return ApiResponse.ok(conversationService.listByAdvisor(agentId));
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
        String senderType = (String) body.getOrDefault("senderType", "CUSTOMER");
        String type = (String) body.getOrDefault("type", "TEXT");

        if (conversationId == null || content == null) {
            throw new IllegalStateException("conversationId 和 content 必填");
        }

        ChatMessage msg = messageService.send(conversationId, userId, senderType, content, type);
        return ApiResponse.ok(msg);
    }

    @GetMapping("/messages/{conversationId}")
    public ApiResponse<Map<String, Object>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "20") int size) {
        List<ChatMessage> msgs = messageService.history(conversationId, size);
        Map<String, Object> result = new HashMap<>();
        result.put("records", msgs);
        result.put("total", msgs.size());
        result.put("conversationId", conversationId);
        return ApiResponse.ok(result);
    }

    @GetMapping("/verify/{conversationId}")
    public ApiResponse<Map<String, Object>> verifyChain(@PathVariable String conversationId) {
        return ApiResponse.ok(messageService.verifyChain(conversationId));
    }
}