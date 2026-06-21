package com.fin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxMiniLoginRequest extends LoginRequest {
    @NotBlank
    private String code;                // wx.login 拿到的 code

    /** 加密的手机号数据 (button open-type=getPhoneNumber 回调) */
    private String encryptedData;
    private String iv;

    /** 可选, 昵称/头像 (getUserProfile) */
    private String nickname;
    private String avatar;
}
