package com.rxt.assist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章评论 —— 对应 t_article_comment（M5）。
 *
 * <p>评论是一次写入、永不修改的数据，所以没有 {@code updateTime}，
 * 也没有逻辑删除列 —— 需求里没有"删除评论"的场景。
 *
 * <p>注意它<b>没有</b> {@code deleted} 属性，因此 MyBatis-Plus 的全局逻辑删除
 * 不会往查询里拼 {@code AND deleted = 0}（表里也没这一列，拼了会直接报
 * {@code Unknown column}）。这与 P1 的 {@code Expert} 实体同理。
 */
@Data
@TableName("t_article_comment")
public class ArticleComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文章 t_article.id */
    private Long articleId;

    /** 评论人 t_user.id */
    private Long userId;

    /** 评论内容，上限 200 字 */
    private String content;

    private LocalDateTime createTime;
}
