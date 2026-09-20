package com.rxt.assist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.assist.service.ExpertService;
import com.rxt.assist.vo.ExpertCardVO;
import com.rxt.assist.vo.ExpertProfileVO;
import com.rxt.common.PageResult;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.ArticleConst;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.entity.Article;
import com.rxt.entity.Expert;
import com.rxt.entity.User;
import com.rxt.mapper.ArticleMapper;
import com.rxt.mapper.ExpertMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 专家服务实现 —— M5（专家部分）。
 *
 * <p><b>核心难点：分页要正确，就不能"先分页再过滤"。</b>
 * 看起来最直接的写法是先 {@code selectPage} 查 t_expert，再把 user.status != 1 的过滤掉 ——
 * 但那样<b>总数会错</b>（total 是过滤前的 count），前端会翻到空页；
 * 而且每页返回的条数会变少，分页参数就失去意义了。
 *
 * <p>正确做法是<b>先把"合法专家的 userId 集合"求出来，再带着这个集合去分页</b>。
 * 这样 total 和每页条数都是对的。代价是要先把所有已通过专家查出来（只取 id 一列），
 * 在演示与试运行的数据量下完全可接受；如果将来专家上万，
 * 就应该改成在 t_expert 上加一列冗余的审核状态，或者直接写一条 JOIN 的 XML 查询 ——
 * 那时再优化，现在为它引入连表 SQL 只会增加复杂度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpertServiceImpl implements ExpertService {

    private static final int MAX_PAGE_SIZE = 200;

    /** 专家详情页"最近文章"的条数 */
    private static final int RECENT_ARTICLE_LIMIT = 5;

    private final ExpertMapper expertMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;

    // ==================================================================================
    // 1. 专家列表
    // ==================================================================================
    @Override
    public PageResult<ExpertCardVO> pageExperts(String specialty, int page, int size) {
        int pageNo = page < 1 ? 1 : page;
        int pageSize = size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);

        // 第一步：找出所有"账号正常、角色是专家"的用户 id。
        // 只 select id 一列（用 wrapper.select 限定），避免把密码等字段捞进内存
        List<Long> approvedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getRole, UserConst.ROLE_EXPERT)
                        .eq(User::getStatus, UserConst.STATUS_NORMAL))
                .stream().map(User::getId).toList();
        if (approvedUserIds.isEmpty()) {
            // 一个已通过的专家都没有。短路返回空分页，连库都不用再查
            return PageResult.empty(pageNo, pageSize);
        }

        // 第二步：带着这个 id 集合去分页查 t_expert。
        // 因为过滤条件已经全部下推到 SQL 的 WHERE 里，total 与每页条数都是准的
        String keyword = StringUtils.hasText(specialty) ? specialty.trim() : null;
        Page<Expert> pageParam = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Expert> wrapper = new LambdaQueryWrapper<Expert>()
                .in(Expert::getUserId, approvedUserIds)
                .like(keyword != null, Expert::getDomain, keyword)
                // 评分高的排前面：农户挑专家时"口碑"是最直接的判断依据
                .orderByDesc(Expert::getRating)
                .orderByDesc(Expert::getId);

        Page<Expert> result = expertMapper.selectPage(pageParam, wrapper);
        PageResult<ExpertCardVO> voPage = PageResult.of(result, ExpertCardVO::from);
        fillAvatars(voPage.getRecords());
        return voPage;
    }

    // ==================================================================================
    // 2. 专家详情
    // ==================================================================================
    @Override
    public ExpertProfileVO expertDetail(Long expertId) {
        Expert expert = expertMapper.selectById(expertId);
        if (expert == null) {
            throw new ResourceNotFoundException("专家不存在");
        }
        User user = userMapper.selectById(expert.getUserId());
        // 账号被禁用/被拒的专家，详情页也不该能打开 ——
        // 否则农户还能进他的主页、发起预约，而对方根本答不了
        if (user == null || !Objects.equals(user.getStatus(), UserConst.STATUS_NORMAL)) {
            throw new BizException(ResultCode.EXPERT_NOT_AVAILABLE, "该专家当前不可预约");
        }

        // 最近文章：只取已发布的，按发布时间倒序
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, expert.getUserId())
                .eq(Article::getStatus, ArticleConst.STATUS_PUBLISHED)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + RECENT_ARTICLE_LIMIT));
        String authorName = expertDisplayName(expert, user);
        List<ArticleVO> recent = articles.stream().map(a -> {
            ArticleVO vo = ArticleVO.from(a);
            vo.setAuthorName(authorName);
            return vo;
        }).collect(Collectors.toList());

        ExpertProfileVO vo = ExpertProfileVO.from(expert, recent);
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    // ==================================================================================
    // 3. 按用户 id 取专家资质（给 ConsultService 用）
    // ==================================================================================
    @Override
    public Expert requireApprovedExpert(Long userId) {
        Expert expert = expertMapper.selectOne(new LambdaQueryWrapper<Expert>()
                .eq(Expert::getUserId, userId)
                .last("LIMIT 1"));
        if (expert == null) {
            // t_expert 里没有行 = 还没通过审核（表39：审核通过才写入）
            throw new BizException(ResultCode.EXPERT_NOT_AVAILABLE,
                    "您还不是通过审核的专家，无法执行该操作");
        }
        // 再看账号状态：审核通过后被禁用的专家，也不应该能回答/处理预约
        User user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getStatus(), UserConst.STATUS_NORMAL)) {
            throw new BizException(ResultCode.EXPERT_NOT_AVAILABLE, "账号状态异常，无法执行专家操作");
        }
        return expert;
    }

    // ==================================================================================
    // 私有辅助
    // ==================================================================================

    /**
     * 批量回填头像。
     *
     * <p>头像存在 {@code t_user.avatar}，不在 t_expert 里（因为它是账号级属性，
     * 五种角色共用），所以列表查完必须补一次。
     * 仍然是一次 {@code selectBatchIds}，不是循环查 —— 这是本项目第四次处理同一个问题，
     * 写法必须一致，否则"有的地方批量、有的地方循环"
     * 迟早会在数据量上来时变成一个查不出来的性能问题。
     */
    private void fillAvatars(List<ExpertCardVO> experts) {
        if (experts == null || experts.isEmpty()) {
            return;
        }
        Set<Long> userIds = experts.stream()
                .map(ExpertCardVO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        experts.forEach(vo -> {
            User user = userMap.get(vo.getUserId());
            if (user != null) {
                vo.setAvatar(user.getAvatar());
            }
        });
    }

    /**
     * 专家展示名：优先 {@code real_name}（实名，比昵称正式），为空时退到 {@code t_user.name}。
     *
     * <p>注意 {@code real_name} 在注册阶段可能为空 —— 它是管理员审核通过时
     * 从 {@code t_user.extend_json} 解出来的，资质不全的账号可能是 null。
     */
    private String expertDisplayName(Expert expert, User user) {
        if (StringUtils.hasText(expert.getRealName())) {
            return expert.getRealName();
        }
        if (user != null && StringUtils.hasText(user.getName())) {
            return user.getName();
        }
        return "未命名专家";
    }
}
