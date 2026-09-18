package com.rxt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核入参 —— 专家审核（{@code PUT /api/admin/expert/{id}/audit}，表39）
 * 与文章审核（{@code PUT /api/article/{id}/audit}，表40）<b>共用</b>这一个 DTO。
 *
 * <p><b>为什么两个接口共用一个 DTO？</b>因为它们的请求体形状完全一致：
 * 一个结果值 + 一段意见。这不是"图省事"，而是因为两个审核场景的业务规则也一致：
 * <ul>
 *   <li>结果只有"通过 / 拒绝"两种；</li>
 *   <li>拒绝时意见必填（表40 明确写"填写理由（拒绝时必须填写）"，
 *       表39 虽然写"审核意见（可选）"，但拒绝时用户需要知道原因，
 *       所以统一按"拒绝必填"处理更合理）。</li>
 * </ul>
 * 如果哪天文章审核多出"打回重改"这类第三态，再拆成两个 DTO 即可。
 *
 * <p>注意这里<b>没有</b> {@code @NotBlank} 之外的业务校验：
 * 取值必须落在 {@code APPROVED / REJECTED} 里、以及"拒绝必填意见"，
 * 都放在 Service 做——因为那些是业务规则，而且需要同一个错误码（1001）配不同文案，
 * 写在 Service 里才能统一用 {@code BizException} 表达。
 */
@Data
public class AuditDTO {

    /** 审核结果：APPROVED 通过 / REJECTED 拒绝 */
    @NotBlank(message = "审核结果不能为空")
    private String status;

    /** 审核意见。拒绝时必填 */
    @Size(max = 255, message = "审核意见不能超过 255 个字符")
    private String remark;
}
