package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("t_user")
public class User {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号，登录账号，全局唯一 */
    private String phone;

    /** BCrypt 密文。绝不允许出现在任何返回给前端的对象里 */
    private String password;

    /** 姓名 / 昵称 */
    private String name;

    /** farmer 农户或买家 / expert 农业专家 / bank 银行人员 / admin 平台管理员 */
    private String role;

    /** 0 待审核 / 1 正常 / 2 已拒绝 / 3 已禁用 */
    private Integer status;

    private String province;
    private String city;
    private String district;
    private String email;
    private String avatar;


    private String extendJson;

    /** 审核意见，拒绝时必填 */
    private String auditRemark;

    /** 连续登录失败次数，登录成功要清零 */
    private Integer failCount;


    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginAt;


    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
