package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提问入参 —— {@code POST /api/question}（M5）。
 *
 * <p>{@code expertId} 可为空 —— 契约写明"可选，不填则所有专家可见"。
 * 为空时该问题进入公共池，任何已通过审核的专家都能回答（先到先得）。
 * 服务端在 {@code ConsultServiceImpl#ask} 里会校验：如果填了，
 * 这个 id 必须是一个<b>已通过资质审核</b>的专家，否则会出现"提问发给了一个
 * 还没审核通过的人"，问题永远没人答。
 *
 * <p>{@code expertId} 是 {@code t_expert.id}，<b>不是 t_user.id</b>。
 */
@Data
public class QuestionDTO {

    /** 指定专家 t_expert.id。不填则面向所有专家 */
    private Long expertId;

    @NotBlank(message = "问题标题不能为空")
    @Size(max = 100, message = "问题标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 500, message = "问题描述不能超过 500 个字符")
    private String content;
}
