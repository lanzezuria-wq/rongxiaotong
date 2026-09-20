package com.rxt.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 融资申请入参 —— {@code POST /api/financing/apply}（M4）。
 *
 * <p>字段和约束逐条对照 OpenAPI 契约里的 {@code FinancingApplyDTO}：
 * amount 必填 10000~500000、usage/industry/province/city/loanTerm/description 必填、
 * description 上限 500 字。
 *
 * <p><b>⚠️ 契约里的枚举值与本项目字典里的编码不一致，这是必须说明的一处冲突：</b>
 * <ul>
 *   <li>契约 {@code usage} = {@code material/expand/facility/other}，
 *       而 P1 建的 {@code t_dict.fund_usage} = {@code SEED/MACHINE/GREENHOUSE/LAND/BREED/PROCESS}；</li>
 *   <li>契约 {@code industry} = {@code planting/breeding/process}，
 *       而 {@code t_dict.industry} = {@code PLANTING/BREEDING/PROCESSING/SERVICE/ECOMMERCE}；</li>
 *   <li>契约 {@code loanTerm} = {@code 6/12/24/36}，
 *       而 {@code t_dict.loan_term} = {@code M3/M6/M12/M24/M36}。</li>
 * </ul>
 *
 * <p><b>处理方式：不加 {@code @Pattern}/enum 注解，把取值校验下沉到 Service 用字典表校验</b>
 * （见 {@code FinancingServiceImpl#assertDictCode}）。理由是字典是<b>可运行时维护</b>的 ——
 * 管理员在"数据字典"页加一项就该能立刻用，硬编码枚举会把它挡在门外。
 * 校验时同时接受"字典里的编码"和"契约里的枚举值"，两套都能过。
 * <b>需要和 P1/P4 定稿统一口径</b>，否则前端下拉框填的值会有一半被拒。
 *
 * <p>金额用 {@link BigDecimal} 且用字符串常量比较 —— 绝不用 {@code double}，
 * 也绝不用 {@code new BigDecimal(0.1)}（那会得到 0.1000000000000000055…）。
 */
@Data
public class FinancingApplyDTO {

    @NotNull(message = "申请金额不能为空")
    @DecimalMin(value = "10000", message = "申请金额不能低于 10000 元")
    @DecimalMax(value = "500000", message = "申请金额不能超过 500000 元")
    private BigDecimal amount;

    @NotBlank(message = "资金用途不能为空")
    private String usage;

    @NotBlank(message = "所属行业不能为空")
    private String industry;

    @NotBlank(message = "省份不能为空")
    private String province;

    @NotBlank(message = "城市不能为空")
    private String city;

    @NotBlank(message = "贷款期限不能为空")
    private String loanTerm;

    @NotBlank(message = "用途说明不能为空")
    @Size(max = 500, message = "用途说明不能超过 500 个字符")
    private String description;
}
