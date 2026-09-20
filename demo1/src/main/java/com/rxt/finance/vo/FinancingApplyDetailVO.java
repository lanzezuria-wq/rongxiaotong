package com.rxt.finance.vo;

import com.rxt.finance.entity.FinancingApply;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 融资申请详情出参 —— 对应契约里的 {@code FinancingApplyDetailVO}。
 *
 * <p>继承 {@link FinancingApplyVO} 而不是复制一遍字段：契约本身就是
 * {@code allOf: [FinancingApplyVO, {description, group, approval}]}，
 * 用继承能保证"详情一定包含列表的所有字段"，不会出现"列表加了字段、详情忘了加"。
 *
 * <p>继承时<b>必须加 {@code @EqualsAndHashCode(callSuper = true)}</b>：
 * Lombok 默认生成的 equals 不比较父类字段，会导致"两个内容不同的详情 VO 被判为相等"，
 * 而且编译器会给出 warning。这是 Lombok 最经典的坑之一，显式声明把它消掉。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancingApplyDetailVO extends FinancingApplyVO {

    /** 用途说明 / 还款来源说明 */
    private String description;

    /**
     * 所属联保小组摘要。待匹配时为 null。
     *
     * <p>只有摘要（不含 members）—— 成员名单在
     * {@code GET /api/financing/group/{id}} 里返回。
     * 详情页要展示"我的申请挂在哪个组、组里几个人、总额多少"，
     * 这些摘要字段够了。
     */
    private GuaranteeGroupVO group;

    /** 银行审批结果。未审批时为 null */
    private BankApprovalVO approval;

    /** 先铺平父类的基础字段，再补详情独有的三个字段 */
    public static FinancingApplyDetailVO from(FinancingApply apply) {
        if (apply == null) {
            return null;
        }
        FinancingApplyDetailVO vo = new FinancingApplyDetailVO();
        copyBase(FinancingApplyVO.from(apply), vo);
        vo.setDescription(apply.getDescription());
        return vo;
    }

    /**
     * 把父类 VO 的字段逐个搬到子类实例上。
     *
     * <p>为什么不用 {@code BeanUtils.copyProperties(base, vo)}？
     * 一是它同样存在"字段名改了不报错"的静默风险；二是这个项目刻意不引 Spring 的
     * bean 工具来做 VO 转换（P1 起就统一用显式赋值）。保持一致的写法比省几行更重要。
     */
    private static void copyBase(FinancingApplyVO base, FinancingApplyDetailVO vo) {
        vo.setId(base.getId());
        vo.setAmount(base.getAmount());
        vo.setUsage(base.getUsage());
        vo.setIndustry(base.getIndustry());
        vo.setProvince(base.getProvince());
        vo.setCity(base.getCity());
        vo.setLoanTerm(base.getLoanTerm());
        vo.setStatus(base.getStatus());
        vo.setStatusName(base.getStatusName());
        vo.setGroupId(base.getGroupId());
        vo.setBankId(base.getBankId());
        vo.setCreatedAt(base.getCreatedAt());
    }
}
