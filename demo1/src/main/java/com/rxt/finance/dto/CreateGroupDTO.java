package com.rxt.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起新联保小组入参 —— {@code POST /api/financing/create-group}（M4）。
 *
 * <p>只要求填"行业 + 省 + 市"，这三样是<b>匹配依据</b>而不是展示信息：
 * 后续 {@code GET /api/financing/groups} 就是按这三样帮别的农户找到能加入的小组，
 * 所以缺一个匹配就失效。
 *
 * <p>三个字段都用 {@code @NotBlank} 而不是 {@code @NotNull}：
 * 空字符串 {@code ""} 能过 {@code @NotNull} 但过不了 {@code @NotBlank}，
 * 而前端表单清空输入框时提交的正是空串 —— 这是最容易漏的一处校验。
 */
@Data
public class CreateGroupDTO {

    @NotBlank(message = "所属行业不能为空")
    @Size(max = 50, message = "所属行业不能超过 50 个字符")
    private String industry;

    @NotBlank(message = "省份不能为空")
    @Size(max = 30, message = "省份不能超过 30 个字符")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 30, message = "城市不能超过 30 个字符")
    private String city;
}
