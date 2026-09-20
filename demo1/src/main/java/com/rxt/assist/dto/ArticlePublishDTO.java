package com.rxt.assist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布文章入参 —— {@code POST /api/article}（M5）。
 *
 * <p>{@code tags} 用 {@code List<String>} 接收，而数据库列是逗号分隔的 VARCHAR，
 * 转换在 Service 里做。契约里就是这么定义的（{@code tags: array of string}），
 * 让前端传数组比让它自己拼逗号更不容易错（比如标签里本身就带逗号）。
 *
 * <p><b>没有 {@code status} 字段，这是有意的：</b>
 * 文章状态由作者角色决定（农户→0 待审核，专家→1 已发布），
 * 不给前端传值的机会 —— 否则农户可以 POST 一个 {@code "status":1} 绕过审核。
 * 这类"不能让客户端决定的状态"必须从 DTO 里彻底拿掉，而不是靠 Service 忽略它。
 */
@Data
public class ArticlePublishDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类不能超过 50 个字符")
    private String category;

    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文过长（上限 20000 字），请拆成多篇")
    private String content;

    /** 封面图地址，可选 */
    @Size(max = 255, message = "封面图地址不能超过 255 个字符")
    private String coverUrl;

    /** 标签，可选。最多 6 个，每个不超过 20 字 */
    @Size(max = 6, message = "标签最多 6 个")
    private List<@Size(max = 20, message = "单个标签不能超过 20 个字符") String> tags;
}
