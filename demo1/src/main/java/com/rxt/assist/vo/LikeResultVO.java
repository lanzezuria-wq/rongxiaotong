package com.rxt.assist.vo;

import com.rxt.entity.Article;
import lombok.Data;

/**
 * 点赞结果 —— 对应契约里 {@code PUT /api/article/{id}/like} 的返回。
 *
 * <p>契约里这个接口的响应只是 {@code R}（没有定义 data），本项目<b>多返回一个对象</b>。
 *
 * <p><b>为什么要加？</b>因为"点赞"是可切换的（再点一次取消）。
 * 如果只返回 {@code {code:0}}，前端得自己猜"这次是赞上了还是取消了"，然后本地 ±1。
 * 一旦请求失败重试、或多端同时操作，本地的推断就会和真实数据错位，
 * 用户看到点赞数跳错。服务端返回"最终状态 + 最终数量"，前端直接覆盖即可，永远不会错。
 *
 * <p>契约外字段不会破坏契约（前端忽略即可），但能让联调少一类 bug，
 * 所以选择加上。<b>已登记在 README 的契约偏差清单里。</b>
 */
@Data
public class LikeResultVO {

    /** 本次操作后的状态：0 未点赞 / 1 已点赞 */
    private Integer isLiked;

    /** 本次操作后的点赞总数（直接覆盖前端本地值） */
    private Integer likeCount;

    /**
     * 从实体状态构造。
     *
     * <p>注意 {@code likeCount} 应当传<b>操作后重新查到的值</b>，
     * 而不是"旧值 ± 1 算出来的值" —— 算出来的值在并发下会偏。
     */
    public static LikeResultVO of(boolean liked, Article article) {
        LikeResultVO vo = new LikeResultVO();
        vo.setIsLiked(liked ? 1 : 0);
        vo.setLikeCount(article == null || article.getLikeCount() == null ? 0 : article.getLikeCount());
        return vo;
    }
}
