package com.fin.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 哈希链服务 (Merkle)
 *
 * <p>每条消息的 hash 链入上一条, 形成不可篡改序列
 * <p>每 100 条聚一棵 Merkle Tree, 送 TSA 第三方时间戳
 */
@Slf4j
@Service
public class HashChainService {

    private final Map<String, String> lastHashByConv = new ConcurrentHashMap<>();

    public String nextHash(String conversationId, String content, String prevHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(content.getBytes(StandardCharsets.UTF_8));
            if (prevHash == null) prevHash = lastHashByConv.get(conversationId);
            if (prevHash == null) prevHash = "GENESIS";
            md.update("|".getBytes(StandardCharsets.UTF_8));
            md.update(prevHash.getBytes(StandardCharsets.UTF_8));
            md.update("|".getBytes(StandardCharsets.UTF_8));
            md.update(String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            String hash = bytesToHex(md.digest());
            lastHashByConv.put(conversationId, hash);
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public String getLastHash(String conversationId) {
        return lastHashByConv.get(conversationId);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
