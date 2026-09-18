package com.rxt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class UpdateInfoDTO {

    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String name;

    /** @Email 对 null 不做校验（可空），只有填了才校验格式 */
    @Email(message = "请输入正确的邮箱地址")
    private String email;

    @Size(max = 30, message = "省份名称过长")
    private String province;

    @Size(max = 30, message = "城市名称过长")
    private String city;

    @Size(max = 30, message = "区县名称过长")
    private String district;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
