package com.rxt.finance.vo;

import lombok.Data;

import java.util.List;

/**
 * 提交融资申请的返回 —— 对应契约里 {@code POST /api/financing/apply} 的
 * data 结构 {@code {id, matchedGroups}}。
 *
 * <p><b>为什么这个接口要返回 matchedGroups？</b>这是本模块体验设计的关键一笔：
 * 农户提交申请后，如果只返回一个 id，他接下来会卡在"我该干什么"——
 * 联保小组要自己去列表里翻。而提交那一刻服务端已经知道他的行业+地域，
 * 完全可以顺手把"能加入的小组"推给他，前端就能直接弹出
 * "为您匹配到 2 个联保小组，立即加入？"
 *
 * <p>匹配规则（同行业 + 同省 + 小组招募中 + 未满员）见
 * {@code FinancingServiceImpl#matchGroups}。
 */
@Data
public class FinancingApplyResultVO {

    /** 新建的融资申请 id */
    private Long id;

    /**
     * 匹配到的可加入联保小组。
     *
     * <p>匹配不到时返回<b>空数组而不是 null</b> —— 前端可以直接判断 {@code length > 0}，
     * 不用先判空。VO 里的集合字段一律给空集合，这是比"返回 null"更省事的约定。
     */
    private List<GuaranteeGroupVO> matchedGroups;

    public static FinancingApplyResultVO of(Long id, List<GuaranteeGroupVO> matchedGroups) {
        FinancingApplyResultVO vo = new FinancingApplyResultVO();
        vo.setId(id);
        vo.setMatchedGroups(matchedGroups == null ? List.of() : matchedGroups);
        return vo;
    }
}
