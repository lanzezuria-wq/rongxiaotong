package com.rxt.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.entity.User;
import lombok.Data;

/**
 * 用户信息出参。
 *
 * <p><b>核心纪律：Entity 绝不直接返回给前端，必须转成 VO。</b>
 * 原因看这个类就够了——{@code User} 里有 {@code password}（BCrypt 密文）、
 * {@code failCount}、{@code lockUntil}、{@code deleted} 这些字段，
 * 一旦把 Entity 直接 return，密码哈希就随响应发出去了。
 * 写成 VO 并只挑"允许前端看到的字段"，从结构上杜绝泄露。
 *
 * <p>用静态工厂 {@link #from(User)} 做转换，比在 Service 里手写一堆
 * {@code vo.setXxx(user.getXxx())} 干净。这里没用 BeanUtil.copyProperties，
 * 因为<b>显式列出字段本身就是一道安全闸</b>——以后 Entity 加了敏感字段，
 * 不会"自动"流到 VO 里。
 *
 * <p><b>日期为什么是 String 而不是 LocalDateTime？</b>
 * 见 {@link DateUtils} 的说明：为了不受 Jackson 主版本影响，保证输出
 * {@code yyyy-MM-dd HH:mm:ss} 这个文档要求的格式。
 */
@Data
public class UserVO {

    private Long id;
    private String phone;
    private String name;
    private String role;
    private Integer status;
    private String province;
    private String city;
    private String district;
    private String email;
    private String avatar;
    /** 角色扩展属性，原样透传的 JSON 字符串，由前端按角色解释 */
    private String extendJson;
    /** 审核意见，专家在看自己被拒原因时需要 */
    private String auditRemark;
    /** 最后登录时间，格式 yyyy-MM-dd HH:mm:ss */
    private String lastLoginAt;
    /** 注册时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createTime;

    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setName(user.getName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setProvince(user.getProvince());
        vo.setCity(user.getCity());
        vo.setDistrict(user.getDistrict());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setExtendJson(user.getExtendJson());
        vo.setAuditRemark(user.getAuditRemark());
        vo.setLastLoginAt(DateUtils.format(user.getLastLoginAt()));
        vo.setCreateTime(DateUtils.format(user.getCreateTime()));
        return vo;
    }
}
