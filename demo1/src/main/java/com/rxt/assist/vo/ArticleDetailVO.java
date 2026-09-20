package com.rxt.assist.vo;

import com.rxt.common.constant.ArticleConst;
import com.rxt.common.util.DateUtils;
import com.rxt.entity.Article;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * 文章详情出参 —— 对应契约里的 {@code ArticleDetailVO}。
 *
 * <p><b>⚠️ 为什么这个类不继承 P1 的 {@code com.rxt.vo.ArticleVO}？</b>
 * 契约里写的是 {@code allOf: [ArticleVO, {...}]}，看起来应该继承，
 * 但两边的 {@code tags} 字段<b>类型冲突</b>：
 * <ul>
 *   <li>P1 的 {@code ArticleVO.tags} 是 {@code String}（对应 t_article.tags 列，
 *       逗号分隔的 VARCHAR），因为审核页只用它做展示；</li>
 *   <li>契约里的 {@code tags} 是 {@code array of string}。</li>
 * </ul>
 * 同一个属性名不能有两种类型，继承就编译不过。所以这里独立定义：
 * 契约字段名照抄，{@code tags} 按契约给数组。
 *
 * <p>另一个差异是 {@code summary}：契约里有它（正文前 100 字）。但 P1 的列表接口是
 * <b>直接返回完整 content</b> 的（为了审核页不用再点详情），所以本类两者都给：
 * {@code summary} 给契约，{@code content} 给详情页。多一个字段不影响前端。
 *
 * <p>{@code isLiked} 用 {@code Integer}（0/1）而不是 {@code boolean}，
 * 与契约一致，也和数据库风格统一。游客访问时恒为 0。
 */
@Data
public class ArticleDetailVO {

    private Long id;
    private String title;
    private String category;

    /** 正文前 100 字。见类注释 */
    private String summary;

    /** 正文全文 */
    private String content;

    private String coverUrl;

    /** 标签数组。由 t_article.tags 的逗号分隔字符串拆出来 */
    private List<String> tags;

    private Long authorId;

    /** 作者姓名，Service 批量回填 */
    private String authorName;

    /** 作者角色（farmer / expert），前端用于显示"农户来稿"角标 */
    private String authorRole;

    /** 0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架 */
    private Integer status;

    private String auditRemark;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;

    /** 当前登录用户是否已点赞：0 否 / 1 是。未登录时恒为 0 */
    private Integer isLiked;

    /** 评论列表（按时间倒序，最多返回最近 50 条） */
    private List<CommentVO> comments;

    private String createdAt;

    /**
     * 实体 → VO。
     *
     * <p>注意 {@code isLiked} 和 {@code comments} <b>不在这里填</b> ——
     * 它们需要额外的查询（点赞记录、评论列表）和当前用户身份，属于 Service 的职责。
     * 静态工厂只负责"实体里有的字段"，这样这个方法才是纯粹的、可预测的。
     */
    public static ArticleDetailVO from(Article article) {
        if (article == null) {
            return null;
        }
        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setCategory(article.getCategory());
        vo.setSummary(summarize(article.getContent()));
        vo.setContent(article.getContent());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setTags(splitTags(article.getTags()));
        vo.setAuthorId(article.getAuthorId());
        vo.setAuthorRole(article.getAuthorRole());
        vo.setStatus(article.getStatus());
        vo.setAuditRemark(article.getAuditRemark());
        vo.setViewCount(article.getViewCount());
        vo.setLikeCount(article.getLikeCount());
        vo.setCommentCount(article.getCommentCount());
        // 默认 0，Service 拿到当前用户后再覆盖
        vo.setIsLiked(0);
        vo.setCreatedAt(DateUtils.format(article.getCreateTime()));
        return vo;
    }

    /**
     * 取正文前 {@code ArticleConst.SUMMARY_LENGTH} 字作为摘要。
     *
     * <p>要处理三个细节，否则列表页会很难看：
     * <ol>
     *   <li><b>去掉换行</b> —— 正文里的换行在摘要里会变成奇怪的空档，压成空格更好读；</li>
     *   <li><b>截断后加省略号</b> —— 让用户知道还有下文；</li>
     *   <li><b>按 codePoint 截而不是 char 截</b> —— Java 的 char 是 UTF-16 单元，
     *       中文在 BMP 内虽然是 1 个 char，但 emoji 是 2 个（代理对），
     *       按 char 截会把代理对劈开，产生乱码"?"。</li>
     * </ol>
     */
    private static String summarize(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        if (flat.codePointCount(0, flat.length()) <= ArticleConst.SUMMARY_LENGTH) {
            return flat;
        }
        int end = flat.offsetByCodePoints(0, ArticleConst.SUMMARY_LENGTH);
        return flat.substring(0, end) + "…";
    }

    /**
     * 把 {@code "番茄,大棚,春季"} 拆成 {@code ["番茄","大棚","春季"]}。
     *
     * <p>要过滤空段：{@code "a,,b"} 或 {@code "a,b,"} 会拆出空字符串，
     * 前端渲染成空标签（一个空的小圆角框）非常明显。
     */
    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
