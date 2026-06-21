package com.fin.kms;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignatureResult {
    /** DER 编码签名 */
    private byte[] signature;
    /** 业务公钥 (用于验签, 一般是加密机返回对应的公钥) */
    private byte[] publicKey;
    /** 算法标识 */
    private String algorithm = "SM2withSM3";
}
