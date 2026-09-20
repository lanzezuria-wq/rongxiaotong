package com.rxt.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址入参 —— 对应契约里 POST /address 和 PUT /address/{id} 的 body。
 *
 * <p>契约里必填的是 {@code receiver} / {@code phone} / {@code detail} 三个，
 * 省市区可以为空（有些村镇地址不好拆）。这里保持同样口径。
 *
 * <p>{@code phone} 用正则校验而不是只校验长度：中国大陆手机号是
 * {@code 1[3-9]开头的11位}。只校验"11 位数字"会放过 {@code 12345678901}，
 * 演示时录进去一个假号，收货环节就穿帮了。
 *
 * <p>{@code isDefault} 传 1 表示设为默认地址，Service 会先把该用户
 * 其他地址的默认标记清掉（同一事务内），保证"默认地址唯一"。
 */
@Data
@Schema(description = "收货地址入参")
public class AddressDTO {

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名不能超过 50 字")
    private String receiver;

    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 20, message = "省份名称过长")
    private String province;

    @Size(max = 20, message = "城市名称过长")
    private String city;

    @Size(max = 20, message = "区县名称过长")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过 255 字")
    private String detail;

    @Schema(description = "是否设为默认地址：0 否 / 1 是")
    private Integer isDefault;
}
