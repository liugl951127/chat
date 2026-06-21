package com.fin.chat.service;

import com.fin.chat.dto.Conversation;
import com.fin.commons.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话服务 (沙箱内存版, 生产接 MySQL)
 */
@Slf4j
@Service
public class ConversationService {

    private final Map<String, Conversation> store = new ConcurrentHashMap<>();

    public Conversation create(Long userId, Long advisorId, String type, String title) {
        String id = IdGenerator.conversationId();
        Conversation c = Conversation.builder()
                .id(id)
                .type(type == null ? "P2P" : type)
                .title(title == null ? "新会话" : title)
                .userId(userId)
                .advisorId(advisorId)
                .status(1)
                .msgCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        store.put(id, c);
        log.info("创建会话: id={}, userId={}", id, userId);
        return c;
    }

    public Conversation get(String id) {
        return store.get(id);
    }

    public List<Conversation> listByUser(Long userId) {
        return store.values().stream()
                .filter(c -> userId.equals(c.getUserId()))
                .sorted(Comparator.comparing(Conversation::getLastMsgAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public void touch(String id, String lastMsgId) {
        Conversation c = store.get(id);
        if (c != null) {
            c.setLastMsgId(lastMsgId);
            c.setLastMsgAt(LocalDateTime.now());
            c.setMsgCount(c.getMsgCount() + 1);
            c.setUpdatedAt(LocalDateTime.now());
        }
    }
}
