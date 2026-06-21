package com.fin.kms;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 加密机客户端 (KMS) 统一接口
 *
 * <p>所有签名/加解密都走这里, 私钥永远不出加密机硬件。
 *
 * <p>两类实现:
 * <ul>
 *   <li>{@link HsmKmsClient}: TCP 连国密硬件加密机 (生产)</li>
 *   <li>{@link SoftKmsClient}: BouncyCastle 软算法 (dev/sandbox, 加 profile 限制)</li>
 * </ul>
 */
public interface KmsGatewayClient {

    /* -------------------- SM4 对称 -------------------- */

    /** SM4-CBC 加密, 密钥由 keyAlias 在加密机内引用 */
    byte[] sm4Encrypt(String keyAlias, byte[] plaintext);

    /** SM4-CBC 解密 */
    byte[] sm4Decrypt(String keyAlias, byte[] ciphertext);

    /* -------------------- SM3 摘要 -------------------- */

    /** SM3 摘要 (hex) */
    String sm3Hash(byte[] data);

    default String sm3Hash(String data) {
        return sm3Hash(data == null ? new byte[0] : data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** SM3 摘要 (hex, 64 字符) */
    default String sm3HashHex(String data) {
        return sm3Hash(data);
    }

    /* -------------------- SM2 签名/验签 -------------------- */

    /** SM2 签名 (私钥在加密机, 返回 DER 编码) */
    SignatureResult sm2Sign(String keyAlias, byte[] data);

    /** SM2 验签 */
    boolean sm2Verify(PublicKey publicKey, byte[] data, byte[] signature);

    /* -------------------- SM2 加密/解密 (密钥交换) -------------------- */

    byte[] sm2Encrypt(PublicKey publicKey, byte[] plaintext);
    byte[] sm2Decrypt(String keyAlias, byte[] ciphertext);

    /* -------------------- JWT 签名密钥 (auth-center 用) -------------------- */

    /** 拿到 JWT 私钥 (内部是加密机引用, 调用时调加密机签名) */
    PrivateKey getJwtSigningKey();

    /* -------------------- 密钥管理 -------------------- */

    /** 生成密钥, 返回 keyAlias (内部用) */
    String generateKey(KeySpec spec);

    /* -------------------- 健康 -------------------- */

    boolean ping();
}
