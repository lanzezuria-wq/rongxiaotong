package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 专家回答入参 —— {@code PUT /api/question/{id}/answer}（M5）。
 *
 * <p>回答内容上限 1000 字（契约里 {@code maxLength: 1000}），比提问的 500 字宽 ——
 * 因为回答通常需要展开讲，还要给操作建议。
 */
@Data
public class AnswerDTO {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 1000, message = "回答内容不能超过 1000 个字符")
    private String answer;
}
