package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文章 —— 对应 t_article。
 *
 * <p><b>⚠️ 这张表是 P1 补建的，需要和负责专家模块（P4）的同学对一下。</b>
 * 情况是这样：P1 的 M7 要交付 8 个接口，第 8 个是
 * {@code PUT /api/article/{id}/audit}（文章审核）——它<b>不在 {@code /api/admin/**} 下</b>，
 * 而是定义在 SRS 表58（专家助力模块 API）里。
 * 但原定的建表范围（P1 的 4 张表）里没有文章表，所以这里补建 t_article，
 * 否则审核接口没有数据可审。
 *
 * <p>字段是<b>按表40 的审核界面需求 + 表58 的接口反推</b>的，不是文档给定的权威结构。
 * 联调前请和 P4 确认字段命名，避免两边各写一版。
 *
 * <p>{@code status} 的取值见 {@code ArticleConst}：0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架。
 * 初始值由作者角色决定（表4 权限矩阵）：农户发布需审核 → 0，专家发布直接生效 → 1。
 */
@Data
@TableName("t_article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 文章分类，取值参考 t_dict 的字典项 */
    private String category;

    /** 正文。P1 阶段按纯文本存储；若前端接入富文本编辑器（如 wangEditor），存 HTML 字符串即可 */
    private String content;

    private String coverUrl;

    /** 标签，逗号分隔 */
    private String tags;

    /** 作者 t_user.id */
    private Long authorId;

    /** 冗余作者角色，列表页过滤"农户待审文章"时不用连表 */
    private String authorRole;

    /** 0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架 */
    private Integer status;

    /** 审核意见。拒绝时必须填写（SRS 表40） */
    private String auditRemark;
    private Long auditBy;
    private LocalDateTime auditTime;

    private Integer viewCount;

    /**
     * 点赞数（P3 补列，见 init_p3.sql 里的 {@code rxt_add_column_if_absent}）。
     *
     * <p>冗余计数：可以由 {@code COUNT(t_article_like)} 算出来，但仍然存一份。
     * 因为文章列表页每行都要显示点赞数，10 行就是 10 次 count 子查询；
     * 而计数只需要在点赞/取消的那一刻同步 ±1。
     * <b>同步逻辑集中在 {@code com.rxt.assist.service.impl.ArticleServiceImpl#toggleLike}，
     * 别处不许直接改这一列。</b>
     */
    private Integer likeCount;

    /**
     * 评论数（P3 补列）。同样是为了列表页免去 count 查询，
     * 由 {@code ArticleServiceImpl#comment} 在插入评论时 +1。
     */
    private Integer commentCount;

    /** 逻辑删除，与 t_user 一致 */
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
