package com.rxt.assist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章点赞 —— 对应 t_article_like（M5）。
 *
 * <p><b>这张表的存在本身就是设计决策，不是"顺手多建一张"：</b>
 * 点赞是<b>可切换</b>的（再点一次取消），所以除了给 {@code t_article.like_count}
 * 做加减，还必须能回答"当前这个用户赞过没有" —— 否则前端刷新后无法判断
 * 按钮该显示"已赞"还是"点赞"（对应 {@code ArticleDetailVO.isLiked}）。
 * 只靠计数列是答不出这个问题的。
 *
 * <p>{@code (articleId, userId)} 上有唯一索引，所以"重复点赞"在数据库层就被挡住了
 * —— 这也是并发下两个相同请求同时到达时唯一可靠的防线
 * （Service 里"先查再插"的写法在并发下会漏）。
 */
@Data
@TableName("t_article_like")
public class ArticleLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    private LocalDateTime createTime;
}
