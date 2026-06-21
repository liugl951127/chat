package com.fin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MobileLoginRequest extends LoginRequest {
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String mobile;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须 6 位数字")
    private String smsCode;
}
