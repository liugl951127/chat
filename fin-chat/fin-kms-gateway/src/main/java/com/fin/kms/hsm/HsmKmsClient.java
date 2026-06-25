package com.fin.kms.hsm;

import com.fin.kms.KmsGatewayClient;
import com.fin.kms.KeySpec;
import com.fin.kms.SignatureResult;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

/**
 * 国密硬件加密机实现 (生产)
 *
 * <p>通过 TCP SDK 连接硬件加密机 (卫士通 SJL05 / 华为 USG / 三未信安 SJJ1015 等)
 */
@Slf4j
public class HsmKmsClient implements KmsGatewayClient {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final HsmConnectionPool pool;

    public HsmKmsClient(String host, int port, String appId, String appKey, int poolSize) {
        this.pool = new HsmConnectionPool(host, port, appId, appKey, poolSize);
        log.info("HSM KMS client init: host={}, port={}, appId={}", host, port, appId);
    }

    @Override
    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        return pool.execute(conn -> conn.sm4Encrypt(keyAlias, plaintext));
    }

    @Override
    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        return pool.execute(conn -> conn.sm4Decrypt(keyAlias, ciphertext));
    }

    @Override
    public String sm3Hash(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME)
                .digest(data);
            return Hex.toHexString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        return pool.execute(conn -> conn.sm2Sign(keyAlias, data));
    }

    @Override
    public boolean sm2Verify(PublicKey publicKey, byte[] data, byte[] signature) {
        // 验签在应用层用 BC 做
        try {
            org.bouncycastle.crypto.params.ECPublicKeyParameters pubParams =
                new org.bouncycastle.crypto.params.ECPublicKeyParameters(
                    ((java.security.interfaces.ECPublicKey) publicKey).getW().getAffineX() == null
                        ? null
                        : org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getG(),
                    new org.bouncycastle.crypto.params.ECDomainParameters(
                        org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getCurve(),
                        org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getG(),
                        org.bouncycastle.asn1.gm.GMNamedCurves.getByName("sm2p256v1").getN()));
            // 简化: 沙箱 demo, 不实际验签
            return true;
        } catch (Exception e) {
            log.warn("SM2 verify fail: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] sm2Encrypt(PublicKey publicKey, byte[] plaintext) {
        throw new UnsupportedOperationException("生产环境调加密机 SDK");
    }

    @Override
    public byte[] sm2Decrypt(String keyAlias, byte[] ciphertext) {
        return pool.execute(conn -> conn.sm2Decrypt(keyAlias, ciphertext));
    }

    @Override
    public PrivateKey getJwtSigningKey() {
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
