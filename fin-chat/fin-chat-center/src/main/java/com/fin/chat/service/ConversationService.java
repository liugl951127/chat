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

    /** 客服 (agent) 主动发起会话 */
    public Conversation agentInitiate(Long agentId, Long customerId, String title) {
        // 查重: 如果已有同 agent+customer 会话, 复用
        for (Conversation c : store.values()) {
            if (agentId.equals(c.getAdvisorId()) && customerId.equals(c.getUserId())
                    && "ADVISOR".equals(c.getType()) && Integer.valueOf(1).equals(c.getStatus())) {
                log.info("复用已有客服会话: {}", c.getId());
                return c;
            }
        }
        String id = IdGenerator.conversationId();
        Conversation c = Conversation.builder()
                .id(id)
                .type("ADVISOR")
                .title(title == null ? "专属客服为您服务" : title)
                .userId(customerId)
                .advisorId(agentId)
                .status(1)
                .msgCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        store.put(id, c);
        log.info("客服主动建会话: agent={} -> customer={} id={}", agentId, customerId, id);
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

    /** 客服查看: 我接的客户会话 */
    public List<Conversation> listByAdvisor(Long advisorId) {
        return store.values().stream()
                .filter(c -> advisorId.equals(c.getAdvisorId()))
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
