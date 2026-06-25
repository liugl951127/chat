package com.fin.chat.service;

import com.fin.chat.dto.ChatMessage;
import com.fin.commons.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息服务 (沙箱内存版, 生产接 MySQL)
 *
 * <p>功能:
 * <ul>
 *   <li>发送消息 (含哈希链)</li>
 *   <li>查询历史消息</li>
 *   <li>验证哈希链完整性
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationService conversationService;
    private final HashChainService hashChainService;

    /** convId -> 消息列表 (按时间正序) */
    private final Map<String, List<ChatMessage>> messageStore = new ConcurrentHashMap<>();

    /**
     * 发送消息 (REST 入口)
     */
    public ChatMessage send(String conversationId, Long senderId, String senderType,
                            String content, String type) {
        // 1. 会话存在性
        if (conversationService.get(conversationId) == null) {
            throw new IllegalStateException("会话不存在: " + conversationId);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("消息内容不能为空");
        }

        // 2. 哈希链
        String prevHash = hashChainService.getLastHash(conversationId);
        String currHash = hashChainService.nextHash(conversationId, content, prevHash);

        // 3. 装消息
        ChatMessage msg = new ChatMessage();
        msg.setMsgId(IdGenerator.msgId());
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderType(senderType == null ? "CUSTOMER" : senderType);
        msg.setType(type == null ? "TEXT" : type);
        msg.setContent(content);
        msg.setContentHash(currHash);
        msg.setPrevHash(prevHash);
        msg.setServerTs(System.currentTimeMillis());

        // 4. 存
        messageStore.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(msg);

        // 5. 更新会话
        conversationService.touch(conversationId, msg.getMsgId());

        log.info("[SEND] conv={} msgId={} sender={} len={} hash={}",
                conversationId, msg.getMsgId(), senderId, content.length(),
                currHash.substring(0, Math.min(12, currHash.length())));
        return msg;
    }

    /**
     * 获取历史消息 (倒序)
     */
    public List<ChatMessage> history(String conversationId, int limit) {
        List<ChatMessage> all = messageStore.getOrDefault(conversationId, List.of());
        int size = Math.min(limit, all.size());
        List<ChatMessage> tail = new ArrayList<>(all.subList(Math.max(0, all.size() - size), all.size()));
        Collections.reverse(tail);
        return tail;
    }

    /**
     * 验证哈希链
     */
    public Map<String, Object> verifyChain(String conversationId) {
        List<ChatMessage> msgs = messageStore.get(conversationId);
        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversationId);
        result.put("messageCount", msgs == null ? 0 : msgs.size());

        if (msgs == null || msgs.isEmpty()) {
            result.put("valid", true);
            result.put("reason", "no message");
            return result;
        }

        // 逐条校验 prevHash 引用一致性
        // (生产端再用 HashChainService 重算 contentHash 验证, 这里只验引用链)
        String expectedPrev = null;
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            if (!Objects.equals(m.getPrevHash(), expectedPrev)) {
                result.put("valid", false);
                result.put("brokenAt", i);
                result.put("reason", "prevHash reference broken at #" + i);
                return result;
            }
            if (m.getContentHash() == null || m.getContentHash().isBlank()) {
                result.put("valid", false);
                result.put("brokenAt", i);
                result.put("reason", "contentHash missing at #" + i);
                return result;
            }
            expectedPrev = m.getContentHash();
        }
        result.put("valid", true);
        result.put("rootHash", expectedPrev);
        result.put("chainLength", msgs.size());
        return result;
    }
}