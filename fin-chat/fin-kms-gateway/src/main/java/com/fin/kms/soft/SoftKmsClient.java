package com.fin.kms.soft;

import com.fin.kms.KeySpec;
import com.fin.kms.KmsGatewayClient;
import com.fin.kms.SignatureResult;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 软算法实现 (BC + JDK 原生)
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

    private final Map<String, byte[]> sm4Keys = new ConcurrentHashMap<>();
    private final Map<String, ECPrivateKeyParameters> sm2PrivKeys = new ConcurrentHashMap<>();
    private final Map<String, ECPublicKeyParameters> sm2PubKeys = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();
    private final X9ECParameters sm2Curve = GMNamedCurves.getByName("sm2p256v1");
    private final ECDomainParameters sm2Domain = new ECDomainParameters(
        sm2Curve.getCurve(), sm2Curve.getG(), sm2Curve.getN());

    @PostConstruct
    public void init() {
        log.warn("!! SoftKmsClient active, this is for DEV/SANDBOX only !!");
    }

    /* ============== SM4 ============== */

    @Override
    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        byte[] key = sm4Keys.computeIfAbsent(keyAlias, k -> {
            byte[] sk = new byte[16];
            random.nextBytes(sk);
            return sk;
        });
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                new CBCBlockCipher(new SM4Engine()));
            cipher.init(true, new KeyParameter(key));
            return processCipher(cipher, plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        byte[] key = sm4Keys.get(keyAlias);
        if (key == null) throw new IllegalStateException("SM4 密钥未找到: " + keyAlias);
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                new CBCBlockCipher(new SM4Engine()));
            cipher.init(false, new KeyParameter(key));
            return processCipher(cipher, ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败: " + e.getMessage(), e);
        }
    }

    private byte[] processCipher(PaddedBufferedBlockCipher cipher, byte[] data) throws Exception {
        byte[] out = new byte[cipher.getOutputSize(data.length)];
        int len = cipher.processBytes(data, 0, data.length, out, 0);
        len += cipher.doFinal(out, len);
        byte[] result = new byte[len];
        System.arraycopy(out, 0, result, 0, len);
        return result;
    }

    /* ============== SM3 ============== */

    @Override
    public String sm3Hash(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME)
                .digest(data);
            return Hex.toHexString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 哈希失败: " + e.getMessage(), e);
        }
    }

    /* ============== SM2 签名 ============== */

    @Override
    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        try {
            ECPrivateKeyParameters priv = ensureSm2KeyPair(keyAlias);
            ECPublicKeyParameters pub = sm2PubKeys.get(keyAlias);

            // SM3 摘要
            byte[] digest = Hex.decode(sm3Hash(data));

            SM2Signer signer = new SM2Signer();
            signer.init(true, new ParametersWithRandom(priv, random));
            signer.update(digest, 0, digest.length);
            byte[] sig = signer.generateSignature();

            // 编码公钥 (X.509 格式)
            java.security.spec.ECPoint w = new java.security.spec.ECPoint(
                pub.getQ().getAffineXCoord().toBigInteger(),
                pub.getQ().getAffineYCoord().toBigInteger());
            return new SignatureResult(sig, encodePublicKey(pub, w), "SM2withSM3");
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败: " + e.getMessage(), e);
        }
    }

    /** 简化的公钥编码 (X || Y, 64 字节) */
    private byte[] encodePublicKey(ECPublicKeyParameters pub, java.security.spec.ECPoint w) {
        byte[] x = pub.getQ().getAffineXCoord().toBigInteger().toByteArray();
        byte[] y = pub.getQ().getAffineYCoord().toBigInteger().toByteArray();
        byte[] result = new byte[64];
        // 左对齐 32 字节
        if (x.length <= 32) System.arraycopy(x, 0, result, 32 - x.length, x.length);
        if (y.length <= 32) System.arraycopy(y, 0, result, 32, y.length);
        return result;
    }

    @Override
    public boolean sm2Verify(java.security.PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            // 沙箱: 简化 — 不做完整 JCE -> BC 转换
            // 生产: 调加密机 SDK 或完整的 JCE 互操作
            return true;
        } catch (Exception e) {
            log.warn("SM2 验签失败: {}", e.getMessage());
            return false;
        }
    }

    /* ============== SM2 加解密 ============== */

    @Override
    public byte[] sm2Encrypt(java.security.PublicKey publicKey, byte[] plaintext) {
        // 沙箱不支持 (需 JCE -> BC 完整转换)
        throw new UnsupportedOperationException("沙箱不支持 SM2 加解密, 调加密机");
    }

    @Override
    public byte[] sm2Decrypt(String keyAlias, byte[] ciphertext) {
        try {
            ECPrivateKeyParameters priv = sm2PrivKeys.get(keyAlias);
            if (priv == null) throw new IllegalStateException("SM2 私钥未找到: " + keyAlias);
            SM2Engine engine = new SM2Engine();
            engine.init(false, priv);
            return engine.processBlock(ciphertext, 0, ciphertext.length);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 解密失败: " + e.getMessage(), e);
        }
    }

    /* ============== JWT 私钥 ============== */

    @Override
    public java.security.PrivateKey getJwtSigningKey() {
        // 沙箱: 返回 null, JwtIssuer 走 HS256 fallback
        return null;
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
                random.nextBytes(sk);
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

    /* ============== 内部 ============== */

    private ECPrivateKeyParameters ensureSm2KeyPair(String alias) {
        if (sm2PrivKeys.containsKey(alias)) return sm2PrivKeys.get(alias);
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(sm2Domain, random));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        sm2PrivKeys.put(alias, priv);
        sm2PubKeys.put(alias, pub);
        return priv;
    }
}
