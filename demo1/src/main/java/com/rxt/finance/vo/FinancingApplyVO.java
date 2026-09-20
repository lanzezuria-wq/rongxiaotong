package com.rxt.finance.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.finance.constant.FinancingConst;
import com.rxt.finance.entity.FinancingApply;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 融资申请出参（列表用）—— 对应契约里的 {@code FinancingApplyVO}。
 *
 * <p>比实体多一个 {@code statusName}。为什么后端翻译而不是前端映射？
 * 申请状态有 6 个（0~5），前端要在<b>三个页面</b>（我的申请列表、申请详情、
 * 银行审批详情）各写一份"status → 中文"的映射，改一处必漏两处。
 * 后端一次给全，前端直接显示。
 *
 * <p>时间字段用 {@code String} 而非 {@code LocalDateTime}，与项目统一约定一致
 * （见 {@code DateUtils} 的类注释）：输出格式 100% 可控，不受 Jackson 主版本变化影响。
 *
 * <p>这里<b>不包含</b> {@code group} 和 {@code approval} 子对象 —— 那两项只在详情接口返回
 * （见 {@link FinancingApplyDetailVO}）。列表页不需要它们，
 * 带上会让每条记录多两次查询。
 */
@Data
public class FinancingApplyVO {

    private Long id;
    private BigDecimal amount;

    /** 资金用途编码。前端要显示中文时调字典接口翻译 */
    private String usage;

    private String industry;
    private String province;
    private String city;

    /** 贷款期限编码，如 M12 */
    private String loanTerm;

    /** 0 待匹配 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝 / 5 已取消 */
    private Integer status;

    /** 状态中文名，由后端翻译（见类注释） */
    private String statusName;

    /** 所属联保小组，待匹配时为 null */
    private Long groupId;

    /** 审批银行 t_user.id，未审批时为 null */
    private Long bankId;

    private String createdAt;

    /**
     * 实体 → VO。
     *
     * <p>写成静态工厂而不是用 {@code BeanUtils.copyProperties}：
     * copyProperties 按字段名反射拷贝，<b>多一个字段少一个字段都不会报错</b>，
     * 改了实体字段名之后 VO 静默变 null，问题极难发现。
     * 显式赋值虽然啰嗦，但 IDE 能重构、编译期能查错。
     */
    public static FinancingApplyVO from(FinancingApply apply) {
        if (apply == null) {
            return null;
        }
        FinancingApplyVO vo = new FinancingApplyVO();
        vo.setId(apply.getId());
        vo.setAmount(apply.getAmount());
        vo.setUsage(apply.getUsage());
        vo.setIndustry(apply.getIndustry());
        vo.setProvince(apply.getProvince());
        vo.setCity(apply.getCity());
        vo.setLoanTerm(apply.getLoanTerm());
        vo.setStatus(apply.getStatus());
        vo.setStatusName(FinancingConst.applyStatusName(apply.getStatus()));
        vo.setGroupId(apply.getGroupId());
        vo.setBankId(apply.getBankId());
        // 实体里的 createTime 对应契约里的 createdAt —— 名字不同，靠这一行对齐
        vo.setCreatedAt(DateUtils.format(apply.getCreateTime()));
        return vo;
    }
}
