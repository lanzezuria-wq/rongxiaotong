package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论入参 —— {@code POST /api/article/{id}/comment}（M5）。
 *
 * <p>单独建一个 DTO 而不是收一个 {@code Map} 或裸 {@code String}：
 * 用 DTO 才能让 {@code @Valid} 生效、让 Swagger 显示字段说明、
 * 也才能在不破坏接口的前提下加字段（比如将来的"回复某条评论"）。
 */
@Data
public class ArticleCommentDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 200, message = "评论内容不能超过 200 个字符")
    private String content;
}
