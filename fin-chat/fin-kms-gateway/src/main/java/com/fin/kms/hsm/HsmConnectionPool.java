package com.fin.kms.hsm;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * HSM 连接池
 *
 * <p>复用 TCP 长连接, 避免频繁握手 (握手有 200-500ms 延迟)
 */
@Slf4j
public class HsmConnectionPool {

    private final BlockingQueue<HsmConnection> pool;
    private final String host;
    private final int port;
    private final String appId;
    private final String appKey;
    private final int maxRetries;

    public HsmConnectionPool(String host, int port, String appId, String appKey, int size) {
        this.host = host;
        this.port = port;
        this.appId = appId;
        this.appKey = appKey;
        this.pool = new LinkedBlockingQueue<>(size);
        this.maxRetries = 3;
        for (int i = 0; i < size; i++) {
            try {
                pool.offer(new HsmConnection(host, port, appId, appKey));
            } catch (IOException e) {
                log.error("HSM 初始连接失败: {}", e.getMessage());
            }
        }
    }

    public <T> T execute(Function<HsmConnection, T> action) {
        HsmConnection conn = null;
        Exception lastEx = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                conn = pool.poll(1, TimeUnit.SECONDS);
                if (conn == null || !conn.isAlive()) {
                    conn = reconnect();
                }
                T result = action.apply(conn);
                pool.offer(conn);
                return result;
            } catch (Exception e) {
                lastEx = e;
                log.warn("HSM 调用失败 (重试 {}/{}): {}", i + 1, maxRetries, e.getMessage());
                if (conn != null) {
                    conn.close();
                    conn = null;
                }
            }
        }
        throw new RuntimeException("HSM 不可用, 已重试 " + maxRetries + " 次", lastEx);
    }

    private HsmConnection reconnect() {
        try {
            return new HsmConnection(host, port, appId, appKey);
        } catch (IOException e) {
            throw new RuntimeException("HSM 重连失败", e);
        }
    }

    public void shutdown() {
        HsmConnection c;
        while ((c = pool.poll()) != null) c.close();
    }
}
