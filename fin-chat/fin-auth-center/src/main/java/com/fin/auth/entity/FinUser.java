package com.fin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_user")
public class FinUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String unionid;
    private String wecomUserid;

    /** SM3 哈希, 用于反查 (不可逆) */
    @TableField("mobile_hash")
    private String mobileHash;

    /** SM4 加密密文 (用于回显/客服查看) */
    @TableField("mobile_enc")
    private byte[] mobileEnc;

    @TableField("id_card_hash")
    private String idCardHash;

    @TableField("real_name_enc")
    private byte[] realNameEnc;

    /** 0=未 1=弱 2=强 */
    @TableField("real_name_status")
    private Integer realNameStatus;

    /** C1-C5 */
    @TableField("risk_level")
    private Integer riskLevel;

    /** 0=注销 1=正常 2=冻结 */
    private Integer status;

    private String nickname;
    private String avatar;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
