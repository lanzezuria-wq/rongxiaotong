package com.rxt.assist.vo;

import com.rxt.entity.Expert;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 专家列表项出参 —— 对应契约里的 {@code ExpertVO}（M5 专家列表页）。
 *
 * <p><b>⚠️ 类名不叫 ExpertVO，是因为 P1 已经占用。</b>
 * P1 的 {@code com.rxt.vo.ExpertVO} 是给"管理员审核专家"页用的，
 * 字段是资质与审核信息（certUrl / auditRemark / auditBy…）。
 * 那个 VO 面向审核，这个面向<b>农户挑选专家</b>，字段需求完全不同，所以不能合并 ——
 * 合并会让审核页拿到 rating 这种对审核无意义的字段，
 * 让专家列表页拿到 auditBy 这种不该暴露给农户的字段。
 *
 * <p>契约字段 → 实体字段的映射（三处不一致，最容易联调出错）：
 * <table border="1">
 *   <tr><th>契约字段</th><th>本项目来源</th></tr>
 *   <tr><td>{@code specialty}</td><td>{@code t_expert.domain}（表里叫 domain）</td></tr>
 *   <tr><td>{@code avatar}</td><td>{@code t_user.avatar}（不在 t_expert 里，要批量回填）</td></tr>
 *   <tr><td>{@code answerCount}</td><td>{@code t_expert.answer_count}（P3 新补的列）</td></tr>
 * </table>
 *
 * <p>{@code certUrl}（资质证明）<b>故意不返回</b>：那是审核材料，
 * 管理员审核时看，公开的专家列表不该暴露专家的证书扫描件。
 */
@Data
public class ExpertCardVO {

    /** t_expert.id。注意不是 t_user.id —— 预约和提问接口传的都是这个 id */
    private Long id;

    private Long userId;

    private String realName;

    /** 职称，如"高级农艺师" */
    private String title;

    /** 专业领域。契约里叫 specialty，本项目来自 t_expert.domain */
    private String specialty;

    /** 所属机构 */
    private String org;

    /** 头像。来自 t_user.avatar，由 Service 批量回填 */
    private String avatar;

    /** 评分 0.00~5.00 */
    private BigDecimal rating;

    /** 已回答问题数 */
    private Integer answerCount;

    public static ExpertCardVO from(Expert expert) {
        if (expert == null) {
            return null;
        }
        ExpertCardVO vo = new ExpertCardVO();
        vo.setId(expert.getId());
        vo.setUserId(expert.getUserId());
        vo.setRealName(expert.getRealName());
        vo.setTitle(expert.getTitle());
        vo.setSpecialty(expert.getDomain());
        vo.setOrg(expert.getOrg());
        vo.setRating(expert.getRating());
        vo.setAnswerCount(expert.getAnswerCount());
        // avatar 不在 t_expert 里，由 Service 从 t_user 回填
        return vo;
    }

    /** 供子类复用字段拷贝（同 {@code GuaranteeGroupVO#copyBaseTo} 的思路，一处维护） */
    protected void copyBaseTo(ExpertCardVO target) {
        target.setId(this.id);
        target.setUserId(this.userId);
        target.setRealName(this.realName);
        target.setTitle(this.title);
        target.setSpecialty(this.specialty);
        target.setOrg(this.org);
        target.setAvatar(this.avatar);
        target.setRating(this.rating);
        target.setAnswerCount(this.answerCount);
    }
}
