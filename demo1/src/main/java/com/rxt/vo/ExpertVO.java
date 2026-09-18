package com.rxt.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.entity.Expert;
import lombok.Data;

/**
 * 专家信息出参。
 *
 * <p>和 {@link UserVO} 是一对：{@code UserVO} 是"账号"信息（手机号、角色、状态），
 * 这个类是"专家资质"信息（职称、机构、领域、简介）。两者通过 {@code userId} 关联。
 *
 * <p><b>为什么要分成两个 VO？</b>因为它们的生命周期不同：
 * 账号从注册那一刻就有，资质要等审核通过才存在。
 * 前端页面上："待审核"的专家只能显示姓名/手机号/注册时间（账号信息），
 * "已通过"的专家才能显示职称和专长（资质信息）。
 * 如果硬塞进一个 VO，就总会有一半字段是 null，前端还得猜"这个 null 是没填还是不存在"。
 */
@Data
public class ExpertVO {

    private Long id;
    /** 关联的用户 id */
    private Long userId;
    private String realName;
    private String title;
    private String org;
    private String domain;
    private String intro;
    private String certUrl;
    private String auditRemark;
    private Long auditBy;
    private String auditTime;
    private String createTime;

    public static ExpertVO from(Expert expert) {
        if (expert == null) {
            return null;
        }
        ExpertVO vo = new ExpertVO();
        vo.setId(expert.getId());
        vo.setUserId(expert.getUserId());
        vo.setRealName(expert.getRealName());
        vo.setTitle(expert.getTitle());
        vo.setOrg(expert.getOrg());
        vo.setDomain(expert.getDomain());
        vo.setIntro(expert.getIntro());
        vo.setCertUrl(expert.getCertUrl());
        vo.setAuditRemark(expert.getAuditRemark());
        vo.setAuditBy(expert.getAuditBy());
        vo.setAuditTime(DateUtils.format(expert.getAuditTime()));
        vo.setCreateTime(DateUtils.format(expert.getCreateTime()));
        return vo;
    }
}
