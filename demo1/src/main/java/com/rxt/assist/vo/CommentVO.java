package com.rxt.assist.vo;

import com.rxt.assist.entity.ArticleComment;
import com.rxt.common.util.DateUtils;
import lombok.Data;

/**
 * 文章评论出参 —— 契约里 {@code ArticleDetailVO.comments} 的元素。
 *
 * <p>契约里评论对象是<b>内联定义</b>的（没有单独的 schema），字段是
 * {@code id / username / content / createdAt}。这里独立成一个类，
 * 好处是可以被 Swagger 正确展开、也便于以后加"回复"、"点赞"等字段。
 *
 * <p>{@code username} 不是表字段 —— t_article_comment 只存 {@code user_id}，
 * 由 Service 批量查用户回填（避免 N+1）。
 */
@Data
public class CommentVO {

    private Long id;

    private Long userId;

    /** 评论人姓名，由 Service 批量回填 */
    private String username;

    private String content;

    private String createdAt;

    public static CommentVO from(ArticleComment comment) {
        if (comment == null) {
            return null;
        }
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(DateUtils.format(comment.getCreateTime()));
        return vo;
    }
}
