package com.rxt.assist.vo;

import com.rxt.entity.Expert;
import com.rxt.vo.ArticleVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 专家详情出参 —— 对应契约里的 {@code ExpertDetailVO}。
 *
 * <p>在列表项基础上多两样：{@code intro}（个人简介）和
 * {@code recentArticles}（该专家最近发布的文章）。
 *
 * <p>为什么把"近期文章"放进专家详情，而不是让前端另外调
 * {@code GET /api/article/list?authorId=xx}？
 * 因为契约里就是内联在 ExpertDetailVO 里的；而且农户进入专家主页时最想知道
 * "这人写过什么、靠不靠谱"，一次请求拿到比两次请求体验好。
 * 代价是详情接口要多查一次文章表 —— 只查 5 条且带 idx_article_author 索引，可以接受。
 *
 * <p>（继承必须带 {@code callSuper = true}，理由见 {@code FinancingApplyDetailVO}。）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExpertProfileVO extends ExpertCardVO {

    /** 个人简介 */
    private String intro;

    /** 该专家最近发布的文章（最多 5 篇，按发布时间倒序） */
    private List<ArticleVO> recentArticles;

    public static ExpertProfileVO from(Expert expert, List<ArticleVO> recentArticles) {
        if (expert == null) {
            return null;
        }
        ExpertProfileVO vo = new ExpertProfileVO();
        ExpertCardVO.from(expert).copyBaseTo(vo);
        vo.setIntro(expert.getIntro());
        vo.setRecentArticles(recentArticles == null ? List.of() : recentArticles);
        return vo;
    }
}
