package com.rxt.vo;

import com.rxt.common.util.DateUtils;
import com.rxt.entity.Article;
import lombok.Data;

/**
 * 文章出参。
 *
 * <p>{@code authorName} 不在 t_article 表里——它是查询时另外装填的。
 * 原因是列表页要显示"谁写的"，而文章表只存 {@code author_id}。
 * 两种做法：
 * <ul>
 *   <li>SQL 连表 / 子查询 —— 一条 SQL 出结果，但要在 Mapper 里手写 XML；</li>
 *   <li><b>批量查作者名再回填</b>（本项目用的）—— 先查出这一页的 10 篇文章，
 *       收集它们的 author_id，用一条 {@code selectBatchIds} 把作者全捞出来做成 Map，
 *       再逐个回填。</li>
 * </ul>
 * 第二种只多一次查询，却是"<b>1 + 1 次</b>"而不是"N + 1 次"——
 * 后者是每个作者单独查一次，10 条数据就 11 次查询。这个区别在数据量上来后是致命的。
 *
 * <p>{@code content} 会随列表一起返回。这是为 M7 文章审核页做的取舍：
 * 审核员要看正文才能判断，列表里带上就不用再点一次详情。
 * 表58 里其实定义了独立的详情接口 {@code GET /api/article/{id}}，
 * 正式做法应该是"列表不带 content + 点开再查详情"。
 * 这里为了少占用 P4 同学的接口范围，暂时合并成一个。<b>需要和 P4 确认。</b>
 */
@Data
public class ArticleVO {

    private Long id;
    private String title;
    private String category;
    private String content;
    private String coverUrl;
    private String tags;
    private Long authorId;
    /** 作者姓名，由 Service 批量回填 */
    private String authorName;
    private String authorRole;
    /** 0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架 */
    private Integer status;
    private String auditRemark;
    private Long auditBy;
    private String auditTime;
    private Integer viewCount;
    private String createTime;

    public static ArticleVO from(Article article) {
        if (article == null) {
            return null;
        }
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setCategory(article.getCategory());
        vo.setContent(article.getContent());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setTags(article.getTags());
        vo.setAuthorId(article.getAuthorId());
        vo.setAuthorRole(article.getAuthorRole());
        vo.setStatus(article.getStatus());
        vo.setAuditRemark(article.getAuditRemark());
        vo.setAuditBy(article.getAuditBy());
        vo.setAuditTime(DateUtils.format(article.getAuditTime()));
        vo.setViewCount(article.getViewCount());
        vo.setCreateTime(DateUtils.format(article.getCreateTime()));
        return vo;
    }
}
