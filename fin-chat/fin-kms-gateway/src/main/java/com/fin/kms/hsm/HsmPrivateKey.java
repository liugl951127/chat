package com.fin.kms.hsm;

import lombok.AllArgsConstructor;

import java.security.PrivateKey;

/**
 * 加密机代理私钥 (用于 JWT 等场景)
 *
 * <p>调用 sign() 时实际去加密机签名, 私钥不外传
 */
@AllArgsConstructor
public class HsmPrivateKey implements PrivateKey {

    private final String keyAlias;
    private final HsmConnectionPool pool;

    @Override
    public String getAlgorithm() {
        return "SM2";
    }

    @Override
    public String getFormat() {
        return "HSM";
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException("私钥不出加密机, 不可导出");
    }
}
