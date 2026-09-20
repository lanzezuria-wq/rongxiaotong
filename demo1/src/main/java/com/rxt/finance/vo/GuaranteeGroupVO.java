package com.rxt.finance.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.finance.constant.FinancingConst;
import com.rxt.finance.entity.GuaranteeGroup;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 联保小组出参（摘要）—— 对应契约里的 {@code GuaranteeGroupVO}。
 *
 * <p><b>{@code maxMembers} 不是从表里读的，而是从常量算的。</b>
 * 它是业务规则（10 人），不是每行数据，所以写在
 * {@link FinancingConst#MAX_MEMBERS} 一处，前端和银行端的判断都引用同一份，
 * 避免"表里存了 10、代码里判断 8"的不一致。
 *
 * <p>{@code remainingToForm} 和 {@code statusName} 是<b>契约外字段</b>（本项目补的）：
 * <ul>
 *   <li>{@code remainingToForm}（还差几人成团）—— 前端列表要显示"还差 2 人成团"，
 *       这个减法要么前端每次算、要么后端算一次给全。后端算能少一次前端出错的机会；</li>
 *   <li>{@code statusName} —— 状态中文名，理由同 {@code FinancingApplyVO}。</li>
 * </ul>
 * 多返回字段不会破坏契约（前端忽略即可），<b>已登记在 README 的契约偏差清单里</b>。
 */
@Data
public class GuaranteeGroupVO {

    private Long id;

    /** 小组编号，形如 LB-20260920-000123 */
    private String groupNo;

    private Long creatorId;

    private String industry;
    private String province;
    private String city;

    /** 组内申请金额合计 */
    private BigDecimal totalAmount;

    /** 当前成员数 */
    private Integer currentMembers;

    /** 成员数上限（业务规则常量，非数据库字段） */
    private Integer maxMembers;

    /** 0 招募中 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝 */
    private Integer status;

    private String statusName;

    /** 还差多少人成团（达到 MIN_MEMBERS）。已成型时为 0 */
    private Integer remainingToForm;

    private String createdAt;

    public static GuaranteeGroupVO from(GuaranteeGroup group) {
        if (group == null) {
            return null;
        }
        GuaranteeGroupVO vo = new GuaranteeGroupVO();
        vo.setId(group.getId());
        vo.setGroupNo(group.getGroupNo());
        vo.setCreatorId(group.getCreatorId());
        vo.setIndustry(group.getIndustry());
        vo.setProvince(group.getProvince());
        vo.setCity(group.getCity());
        vo.setTotalAmount(group.getTotalAmount());
        vo.setCurrentMembers(group.getCurrentMembers());
        vo.setMaxMembers(FinancingConst.MAX_MEMBERS);
        vo.setStatus(group.getStatus());
        vo.setStatusName(FinancingConst.groupStatusName(group.getStatus()));
        vo.setRemainingToForm(remainingToForm(group.getCurrentMembers()));
        vo.setCreatedAt(DateUtils.format(group.getCreateTime()));
        return vo;
    }

    /**
     * 供子类（详情/银行端）复用自己的字段拷贝逻辑。
     *
     * <p>为什么做成父类的 protected 方法，而不是在每个子类的静态工厂里各写一遍？
     * 因为子类有<b>两个</b>（{@link GuaranteeGroupDetailVO} 和 {@link BankGroupVO}），
     * 再抄两遍就是三份重复的字段赋值 —— 将来父类加一个字段要改三处，必漏。
     * 放在父类里一处维护，子类只需追加自己独有的字段。
     */
    protected void copyBaseTo(GuaranteeGroupVO target) {
        target.setId(this.id);
        target.setGroupNo(this.groupNo);
        target.setCreatorId(this.creatorId);
        target.setIndustry(this.industry);
        target.setProvince(this.province);
        target.setCity(this.city);
        target.setTotalAmount(this.totalAmount);
        target.setCurrentMembers(this.currentMembers);
        target.setMaxMembers(this.maxMembers);
        target.setStatus(this.status);
        target.setStatusName(this.statusName);
        target.setRemainingToForm(this.remainingToForm);
        target.setCreatedAt(this.createdAt);
    }

    /**
     * 还差几个人成团。
     *
     * <p>{@code currentMembers} 理论上不会为 null（建表有 NOT NULL DEFAULT 0），
     * 但实体字段是包装类型，从别处 new 出来的对象可能是 null，
     * 所以这里按 0 处理，让方法对任何输入都成立 —— 工具型方法不该抛 NPE。
     */
    private static Integer remainingToForm(Integer currentMembers) {
        int members = currentMembers == null ? 0 : currentMembers;
        return Math.max(0, FinancingConst.MIN_MEMBERS - members);
    }
}
