package com.fin.kms.soft;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.SM3;
import cn.hutool.crypto.symmetric.SM4;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import com.fin.kms.KmsGatewayClient;
import com.fin.kms.KeySpec;
import com.fin.kms.SignatureResult;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软算法实现 (BC + Hutool)
 *
 * <p>⚠️ 仅用于 dev/sandbox 沙箱环境, 严禁生产部署!
 * <p>启用方式: {@code fin.kms.soft-fallback.enabled=true}
 * <p>生产必须用 {@link com.fin.kms.hsm.HsmKmsClient} 接国密硬件加密机
 */
@Slf4j
public class SoftKmsClient implements KmsGatewayClient {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** 模拟加密机的 SM4 会话密钥 (按 keyAlias 存) */
    private final Map<String, byte[]> sm4Keys = new ConcurrentHashMap<>();

    /** 模拟加密机的 SM2 私钥 (按 keyAlias 存) */
    private final Map<String, java.security.PrivateKey> sm2PrivKeys = new ConcurrentHashMap<>();
    private final Map<String, java.security.PublicKey> sm2PubKeys = new ConcurrentHashMap<>();

    /** JWT 私钥 (启动时生成) */
    private ECPrivateKey jwtPrivateKey;

    @PostConstruct
    public void init() {
        log.warn("!! SoftKmsClient active, this is for DEV/SANDBOX only !!");
        try {
            // 生成 JWT 用的 SM2 密钥对 (沙箱演示)
            KeyPairGeneratorSpi kpg = new KeyPairGeneratorSpi();
            this.jwtPrivateKey = kpg.generateSm2KeyPair().getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("生成 JWT SM2 密钥失败", e);
        }
    }

    /* ============== SM4 ============== */

    @Override
    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        byte[] key = sm4Keys.computeIfAbsent(keyAlias, k -> {
            byte[] sk = new byte[16];
            new SecureRandom().nextBytes(sk);
            return sk;
        });
        SymmetricCrypto sm4 = SM4.builder()
                .setSecretKey(key)
                .setIv(new byte[16])    // CBC IV=0 (沙箱演示)
                .setMode(SM4.Mode.CBC)
                .setPadding("PKCS7Padding")
                .build();
        return sm4.encrypt(plaintext);
    }

    @Override
    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        byte[] key = sm4Keys.get(keyAlias);
        if (key == null) throw new IllegalStateException("SM4 密钥未找到: " + keyAlias);
        SymmetricCrypto sm4 = SM4.builder()
                .setSecretKey(key)
                .setIv(new byte[16])
                .setMode(SM4.Mode.CBC)
                .setPadding("PKCS7Padding")
                .build();
        return sm4.decrypt(ciphertext);
    }

    /* ============== SM3 ============== */

    @Override
    public String sm3Hash(byte[] data) {
        SM3 sm3 = SecureUtil.sm3();
        return sm3.digestHex(data);
    }

    /* ============== SM2 签名 ============== */

    @Override
    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        try {
            // 1. 准备 SM2 密钥对 (沙箱现生成, 生产在加密机)
            KeyPair kp = ensureSm2KeyPair(keyAlias);

            // 2. SM3 摘要
            byte[] digest = Hex.decode(sm3Hash(data));

            // 3. SM2 签名 (带 Z 值, 国密规范)
            X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domain = new ECDomainParameters(x9.getCurve(), x9.getG(), x9.getN());
            ECPrivateKeyParameters priv = new ECPrivateKeyParameters(
                    ((ECPrivateKey) kp.getPrivate()).getS(), domain);

            SM2Signer signer = new SM2Signer();
            signer.init(true, new ParametersWithRandom(priv, new SecureRandom()));
            signer.update(digest, 0, digest.length);
            byte[] sig = signer.generateSignature();

            return new SignatureResult(sig, kp.getPublic().getEncoded(), "SM2withSM3");
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean sm2Verify(java.security.PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domain = new ECDomainParameters(x9.getCurve(), x9.getG(), x9.getN());
            ECPublicKeyParameters pub = new ECPublicKeyParameters(
                    ((ECPublicKey) publicKey).getW(), domain);
            byte[] digest = Hex.decode(sm3Hash(data));
            SM2Signer signer = new SM2Signer();
            signer.init(false, pub);
            signer.update(digest, 0, digest.length);
            return signer.verifySignature(signature);
        } catch (Exception e) {
            log.warn("SM2 验签失败: {}", e.getMessage());
            return false;
        }
    }

    /* ============== SM2 加解密 (C1C3C2) ============== */

    @Override
    public byte[] sm2Encrypt(java.security.PublicKey publicKey, byte[] plaintext) {
        try {
            X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domain = new ECDomainParameters(x9.getCurve(), x9.getG(), x9.getN());
            ECPublicKeyParameters pub = new ECPublicKeyParameters(
                    ((ECPublicKey) publicKey).getW(), domain);
            SM2Engine engine = new SM2Engine();
            engine.init(true, new ParametersWithRandom(pub, new SecureRandom()));
            return engine.processBlock(plaintext, 0, plaintext.length);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    @Override
    public byte[] sm2Decrypt(String keyAlias, byte[] ciphertext) {
        try {
            java.security.PrivateKey priv = sm2PrivKeys.get(keyAlias);
            if (priv == null) throw new IllegalStateException("SM2 私钥未找到: " + keyAlias);
            X9ECParameters x9 = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domain = new ECDomainParameters(x9.getCurve(), x9.getG(), x9.getN());
            ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(
                    ((ECPrivateKey) priv).getS(), domain);
            SM2Engine engine = new SM2Engine();
            engine.init(false, privParams);
            return engine.processBlock(ciphertext, 0, ciphertext.length);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 解密失败", e);
        }
    }

    /* ============== JWT 私钥 ============== */

    @Override
    public java.security.PrivateKey getJwtSigningKey() {
        return jwtPrivateKey;
    }

    /* ============== 密钥生成 ============== */

    @Override
    public String generateKey(KeySpec spec) {
        String alias = spec.fullAlias();
        if ("SM2".equalsIgnoreCase(spec.getAlgorithm())) {
            ensureSm2KeyPair(alias);
        } else if ("SM4".equalsIgnoreCase(spec.getAlgorithm())) {
            sm4Keys.computeIfAbsent(alias, k -> {
                byte[] sk = new byte[16];
                new SecureRandom().nextBytes(sk);
                return sk;
            });
        }
        log.info("生成密钥: alias={}, algo={}", alias, spec.getAlgorithm());
        return alias;
    }

    /* ============== 健康 ============== */

    @Override
    public boolean ping() {
        try {
            sm3Hash("ping");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* ============== 内部工具 ============== */

    private KeyPair ensureSm2KeyPair(String alias) {
        if (sm2PrivKeys.containsKey(alias)) {
            return new KeyPair(sm2PubKeys.get(alias), sm2PrivKeys.get(alias));
        }
        try {
            java.security.KeyPairGenerator kpg =
                    java.security.KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec("sm2p256v1"));
            KeyPair kp = kpg.generateKeyPair();
            sm2PrivKeys.put(alias, kp.getPrivate());
            sm2PubKeys.put(alias, kp.getPublic());
            return kp;
        } catch (Exception e) {
            throw new IllegalStateException("生成 SM2 密钥对失败", e);
        }
    }

    /** 沙箱 SM2 密钥对生成 (命名歧义避免冲突) */
    private static class KeyPairGeneratorSpi {
        KeyPair generateSm2KeyPair() throws Exception {
            java.security.KeyPairGenerator kpg =
                    java.security.KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec("sm2p256v1"));
            return kpg.generateKeyPair();
        }
    }
}
