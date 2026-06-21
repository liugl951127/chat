package com.fin.kms.hsm;

import com.fin.kms.KmsGatewayClient;
import com.fin.kms.KeySpec;
import com.fin.kms.SignatureResult;
import lombok.extern.slf4j.Slf4j;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 国密硬件加密机实现 (生产)
 *
 * <p>通过 TCP SDK 连接硬件加密机 (卫士通 SJL05 / 华为 USG / 三未信安 SJJ1015 等)
 * <p>所有私钥物理存储在加密机内, 调用只返回结果, 私钥永不外泄
 *
 * <p>具体协议因厂商而异, 这里以卫士通 SJL05 SDK 为例 (主流国产选型):
 * <pre>
 *   1. TCP 握手 + 国密 IPSec 通道建立
 *   2. 双向证书认证 (客户端证书 + 加密机证书)
 *   3. 应用 ID + 密钥认证
 *   4. 调用各算法接口 (SM2_SIGN / SM4_ENC / SM3_HASH)
 *   5. 短连接 (用完归还) / 长连接 (复用)
 * </pre>
 */
@Slf4j
public class HsmKmsClient implements KmsGatewayClient {

    private final HsmConnectionPool pool;

    public HsmKmsClient(String host, int port, String appId, String appKey, int poolSize) {
        this.pool = new HsmConnectionPool(host, port, appId, appKey, poolSize);
        log.info("HSM KMS client init: host={}, port={}, appId={}", host, port, appId);
    }

    @Override
    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        return pool.execute(conn -> {
            // SJL05 协议: 0x05 0x01 0x00 0x00 | alias_len(2) | alias | data_len(4) | data
            // SDK 封装后一般直接调对称加密接口
            return conn.sm4Encrypt(keyAlias, plaintext);
        });
    }

    @Override
    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        return pool.execute(conn -> conn.sm4Decrypt(keyAlias, ciphertext));
    }

    @Override
    public String sm3Hash(byte[] data) {
        // SM3 哈希一般在 JVM 内做 (BouncyCastle), 性能 50k/s, 远高于加密机
        // 如果监管要求必须用硬件, 改成 pool.execute(conn -> conn.sm3Hash(data))
        return cn.hutool.crypto.SecureUtil.sm3().digestHex(data);
    }

    @Override
    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        return pool.execute(conn -> {
            // 1. SM3 摘要 (在加密机内做, 符合 GM/T 0003)
            // 2. SM2 签名 (带 Z 值, 私钥在加密机内)
            return conn.sm2Sign(keyAlias, data);
        });
    }

    @Override
    public boolean sm2Verify(PublicKey publicKey, byte[] data, byte[] signature) {
        // 验签一般在应用层做 (公钥可导出)
        return cn.hutool.crypto.SecureUtil.sm2().setPublicKey(publicKey)
                .verify(data, signature);
    }

    @Override
    public byte[] sm2Encrypt(PublicKey publicKey, byte[] plaintext) {
        return cn.hutool.crypto.SecureUtil.sm2().setPublicKey(publicKey)
                .encrypt(plaintext, org.bouncycastle.crypto.engines.SM2Engine.Mode.C1C3C2);
    }

    @Override
    public byte[] sm2Decrypt(String keyAlias, byte[] ciphertext) {
        return pool.execute(conn -> conn.sm2Decrypt(keyAlias, ciphertext));
    }

    @Override
    public PrivateKey getJwtSigningKey() {
        // JWT 签名 keyAlias 是固定的, 在加密机内
        // 这里返回一个代理 PrivateKey, 调用 sign 时实际走加密机
        return new HsmPrivateKey("jwt-signing", pool);
    }

    @Override
    public String generateKey(KeySpec spec) {
        return pool.execute(conn -> conn.generateKey(spec));
    }

    @Override
    public boolean ping() {
        try {
            return pool.execute(HsmConnection::ping);
        } catch (Exception e) {
            log.error("HSM ping failed", e);
            return false;
        }
    }
}
