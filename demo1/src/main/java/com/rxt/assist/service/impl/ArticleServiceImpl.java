package com.rxt.assist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.assist.dto.ArticleCommentDTO;
import com.rxt.assist.dto.ArticlePublishDTO;
import com.rxt.assist.entity.ArticleComment;
import com.rxt.assist.entity.ArticleLike;
import com.rxt.assist.mapper.ArticleCommentMapper;
import com.rxt.assist.mapper.ArticleLikeMapper;
import com.rxt.assist.service.ArticleService;
import com.rxt.assist.vo.ArticleDetailVO;
import com.rxt.assist.vo.CommentVO;
import com.rxt.assist.vo.LikeResultVO;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.ArticleConst;
import com.rxt.common.constant.DictConst;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.entity.Article;
import com.rxt.entity.DictItem;
import com.rxt.entity.User;
import com.rxt.mapper.ArticleMapper;
import com.rxt.mapper.DictItemMapper;
import com.rxt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章服务实现 —— M5（文章部分）。
 *
 * <p><b>这个类里最值得说的是"谁来改计数字段"。</b>
 * {@code t_article} 上有 4 个计数：{@code view_count}、{@code like_count}、
 * {@code comment_count}，都是冗余值。如果各处直接
 * {@code setXxx(旧值 + 1)} 再 updateById，并发时两个请求会读到同一个旧值、
 * 各写回 +1，<b>两次点赞只涨 1</b>。
 *
 * <p>所以这里的写法是：用 SQL 表达式做自增
 * （{@code setSql("like_count = like_count + 1")}），
 * 让数据库在行锁保护下自己算。这样无论多少并发都不会丢计数。
 * 代价是更新完要重新查一次才能拿到最终值 —— 但这一次查询值得，
 * 因为返回给前端的必须是"真实的最终值"（见 {@code LikeResultVO} 的注释）。
 *
 * <p>另外所有减法计数都加了<b>下界保护</b> {@code GREATEST(0, x - 1)}：
 * 万一因为历史数据不一致减到 -1，前端会显示"-1 个赞"，
 * 这种脏数据比"少显示一个赞"难看得多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    /** 详情页返回的评论条数上限。全量返回会在热门文章下把响应体撑爆 */
    private static final int COMMENT_PAGE_LIMIT = 50;

    private final ArticleMapper articleMapper;
    private final ArticleCommentMapper commentMapper;
    private final ArticleLikeMapper likeMapper;
    private final DictItemMapper dictItemMapper;
    private final UserMapper userMapper;

    // ==================================================================================
    // 1. 发布文章
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, String role, ArticlePublishDTO dto) {
        // SRS 表4 权限矩阵：只有农户和专家能发布知识文章。
        // 银行人员是审批方、管理员是审核方，都不该以个人身份发文
        // （管理员要发文应该走后台公告，那是另一套东西）
        boolean isFarmer = UserConst.ROLE_FARMER.equals(role);
        boolean isExpert = UserConst.ROLE_EXPERT.equals(role);
        if (!isFarmer && !isExpert) {
            throw new BizException(ResultCode.FORBIDDEN, "只有农户和专家可以发布知识文章");
        }

        String category = assertCategory(dto.getCategory());

        User author = userMapper.selectById(userId);
        if (author == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        Article article = new Article();
        article.setTitle(dto.getTitle().trim());
        article.setCategory(category);
        article.setContent(dto.getContent());
        article.setCoverUrl(dto.getCoverUrl());
        article.setTags(joinTags(dto.getTags()));
        article.setAuthorId(userId);
        // 冗余作者角色：审核列表要按"农户待审文章"筛选，存一份就免了连表
        article.setAuthorRole(role);
        // 关键的一行：状态由角色决定，不接受前端传值
        article.setStatus(isExpert ? ArticleConst.STATUS_PUBLISHED : ArticleConst.STATUS_PENDING);
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCommentCount(0);
        article.setDeleted(0);
        articleMapper.insert(article);

        log.info("{}（角色 {}）发布文章 {}：《{}》，初始状态 {}", userId, role, article.getId(),
                article.getTitle(), isExpert ? "已发布（专家直发）" : "待审核（农户需审核）");
        return article.getId();
    }

    /**
     * 标签列表 → 逗号分隔字符串。
     *
     * <p>要过滤空标签（前端传 {@code ["a", "", "b"]} 时不能存成 {@code "a,,b"}），
     * 并做去重 —— 同一篇文章里出现两个"番茄"是脏数据。
     * 用 {@code LinkedHashSet} 而不是 {@code HashSet}：保留用户输入的顺序，
     * 展示时标签顺序才是稳定的。
     */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> distinct = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return distinct.isEmpty() ? null : String.join(",", distinct);
    }

    /**
     * 分类校验：契约枚举与数据字典取并集。
     *
     * <p>见 {@link ArticleConst#CATEGORY_ENUM} 里对"契约枚举 vs P1 种子数据"冲突的完整说明。
     *
     * <p>注意这里查字典时<b>不限定 type</b>：P1 的种子文章用的分类是
     * {@code VEG / GRAIN / CASH}，它们属于 {@code product_category}；而 P2 的商品表
     * 也用 {@code product_category}。取"任何类型下存在该编码"是当下成本和风险最低的做法，
     * 但也正因为宽松，<b>这一处需要和 P1/P2/P4 定稿后再收紧</b>。
     */
    private String assertCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw new BizException(ResultCode.PARAM_ERROR, "文章分类不能为空");
        }
        String value = category.trim();

        // 一级：契约枚举（planting / breeding / market / policy / experience）
        for (String candidate : ArticleConst.CATEGORY_ENUM) {
            if (candidate.equalsIgnoreCase(value)) {
                return candidate.toLowerCase();
            }
        }

        // 二级：数据字典里存在该编码（不限定 type，理由见方法注释）
        Long hit = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getCode, value)
                .eq(DictItem::getEnabled, DictConst.ENABLED));
        if (hit != null && hit > 0) {
            return value;
        }

        throw new BizException(ResultCode.PARAM_ERROR,
                "文章分类取值不合法，可选：" + String.join(" / ", ArticleConst.CATEGORY_ENUM)
                        + "，或数据字典中已启用的分类编码");
    }

    // ==================================================================================
    // 2. 文章详情
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleDetailVO detail(Long articleId, Long currentUserId, String role) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }

        boolean isPublished = Objects.equals(article.getStatus(), ArticleConst.STATUS_PUBLISHED);
        boolean isAuthor = currentUserId != null && Objects.equals(article.getAuthorId(), currentUserId);
        boolean isAdmin = UserConst.ROLE_ADMIN.equals(role);
        if (!isPublished && !isAuthor && !isAdmin) {
            // 这里故意不返回"文章已被拒绝"之类的细节 —— 对外统一说"不可访问"，
            // 免得泄露"这篇文章存在但出了问题"
            throw new BizException(ResultCode.ARTICLE_NOT_PUBLISHED, "该文章暂未发布，无法查看");
        }

        ArticleDetailVO vo = ArticleDetailVO.from(article);

        // 浏览量自增。用 setSql 让数据库自己 +1，避免并发丢计数
        if (isPublished) {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .setSql("view_count = view_count + 1"));
            // 本次访问的计数要反映到返回值里，否则用户会看到"刷了一次还是 128"
            vo.setViewCount((article.getViewCount() == null ? 0 : article.getViewCount()) + 1);
        }

        // 作者姓名
        if (article.getAuthorId() != null) {
            vo.setAuthorName(resolveName(article.getAuthorId(), "未知用户"));
        }

        // 当前用户是否已点赞（未登录时不查库，直接 0）
        if (currentUserId != null) {
            Long liked = likeMapper.selectCount(new LambdaQueryWrapper<ArticleLike>()
                    .eq(ArticleLike::getArticleId, articleId)
                    .eq(ArticleLike::getUserId, currentUserId));
            vo.setIsLiked(liked != null && liked > 0 ? 1 : 0);
        }

        // 评论列表（最近 N 条，按时间倒序）
        vo.setComments(loadComments(articleId));
        return vo;
    }

    /**
     * 载入评论并回填评论人姓名。
     *
     * <p>同样是一次批量查用户而不是逐个查（N+1 问题）。
     */
    private List<CommentVO> loadComments(Long articleId) {
        List<ArticleComment> comments = commentMapper.selectList(new LambdaQueryWrapper<ArticleComment>()
                .eq(ArticleComment::getArticleId, articleId)
                .orderByDesc(ArticleComment::getCreateTime)
                .orderByDesc(ArticleComment::getId)
                .last("LIMIT " + COMMENT_PAGE_LIMIT));
        if (comments.isEmpty()) {
            return List.of();
        }
        List<CommentVO> vos = comments.stream().map(CommentVO::from).collect(Collectors.toList());

        Set<Long> userIds = comments.stream()
                .map(ArticleComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return vos;
        }
        Map<Long, String> nameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> displayName(u), (a, b) -> a));
        vos.forEach(v -> v.setUsername(nameMap.getOrDefault(v.getUserId(), "未知用户")));
        return vos;
    }

    /** 按 id 取显示名，查不到给兜底 */
    private String resolveName(Long userId, String fallback) {
        User user = userMapper.selectById(userId);
        return user == null ? fallback : displayName(user);
    }

    /** 姓名兜底：t_user.name 允许为空，空的时候不能让前端显示空白 */
    private String displayName(User user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : "未命名用户";
    }

    // ==================================================================================
    // 3. 点赞 / 取消点赞
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResultVO toggleLike(Long userId, Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        // 只能给已发布的文章点赞 —— 给待审文章点赞没有意义
        if (!Objects.equals(article.getStatus(), ArticleConst.STATUS_PUBLISHED)) {
            throw new BizException(ResultCode.ARTICLE_NOT_PUBLISHED, "该文章尚未发布，无法点赞");
        }

        ArticleLike exist = likeMapper.selectOne(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, userId));

        boolean liked;
        if (exist == null) {
            // 点赞：先写关系表（唯一索引会挡住重复），再自增计数
            ArticleLike like = new ArticleLike();
            like.setArticleId(articleId);
            like.setUserId(userId);
            like.setCreateTime(LocalDateTime.now());
            likeMapper.insert(like);
            // GREATEST(0, ...) 在加法上用不上，但两个分支保持同样写法，
            // 以后有人复制这段代码去改逻辑时不会漏掉下界保护
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .setSql("like_count = GREATEST(0, like_count + 1)"));
            liked = true;
        } else {
            // 取消点赞：先删关系表，再自减计数
            likeMapper.deleteById(exist.getId());
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .setSql("like_count = GREATEST(0, like_count - 1)"));
            liked = false;
        }

        // 重新查一次拿最终计数。不用"旧值 ± 1"算：并发下算出来的数会偏，
        // 而返回给前端的必须是真值（前端会直接覆盖本地显示）
        Article latest = articleMapper.selectById(articleId);
        log.debug("用户 {} {}文章 {}", userId, liked ? "点赞" : "取消点赞", articleId);
        return LikeResultVO.of(liked, latest);
    }

    // ==================================================================================
    // 4. 发表评论
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void comment(Long userId, Long articleId, ArticleCommentDTO dto) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (!Objects.equals(article.getStatus(), ArticleConst.STATUS_PUBLISHED)) {
            throw new BizException(ResultCode.ARTICLE_NOT_PUBLISHED, "该文章尚未发布，无法评论");
        }

        ArticleComment comment = new ArticleComment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setContent(dto.getContent().trim());
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .setSql("comment_count = GREATEST(0, comment_count + 1)"));

        String content = comment.getContent();
        log.info("用户 {} 评论文章 {}：{}", userId, articleId,
                content != null && content.length() > 20 ? content.substring(0, 20) + "…" : content);
    }
}
