package com.rxt.assist.service;

import com.rxt.common.PageResult;
import com.rxt.assist.vo.ExpertCardVO;
import com.rxt.assist.vo.ExpertProfileVO;
import com.rxt.entity.Expert;

/**
 * 专家服务 —— M5 专家助力（专家列表与详情，2 个接口）。
 *
 * <p><b>这个模块里最容易搞错的一件事：有两个"专家 id"。</b>
 * <ul>
 *   <li>{@code t_user.id} —— 账号 id。登录、发布文章用它；</li>
 *   <li>{@code t_expert.id} —— 资质记录的 id。
 *       <b>专家列表、详情、提问、预约全部用这个</b>。</li>
 * </ul>
 * 两者一对一但值不同。P1 的表39 说"专家审核通过后写入 t_expert"，
 * 所以一个用户可能是 {@code role=expert} 但 {@code t_expert} 里还没有行
 * （status=0 待审核）—— 这种人<b>不应出现在专家列表里</b>，
 * 因为农户找他提问他也答不了（{@code ConsultService} 会拒绝非已审核专家回答）。
 *
 * <p>所以本模块的查询条件是"<b>t_expert 有记录 且 对应用户 status=1</b>"，两个条件都要。
 * 只查 t_expert 会漏掉"审核通过后被管理员禁用"的账号；
 * 只查 t_user 会把待审核的专家也列出来。
 */
public interface ExpertService {

    /**
     * 已审核通过的专家列表 —— {@code GET /api/expert/list}（公开，游客可看）。
     *
     * @param specialty 专业领域关键词，模糊匹配 {@code t_expert.domain}。可空
     */
    PageResult<ExpertCardVO> pageExperts(String specialty, int page, int size);

    /**
     * 专家详情 —— {@code GET /api/expert/{id}}（公开）。
     *
     * <p>比列表多"个人简介"和"最近发布的文章（最多 5 篇）"。
     *
     * @param expertId {@code t_expert.id}，不是 t_user.id
     */
    ExpertProfileVO expertDetail(Long expertId);

    /**
     * 按用户 id 取专家资质记录，不存在或未通过审核则抛 4001。
     *
     * <p>这个方法给 {@code ConsultService} 用（专家要回答提问、处理预约，
     * 都得先确认"你确实是个已通过审核的专家、你的 t_expert.id 是多少"）。
     *
     * <p>为什么不直接返回 {@code Long expertId}？因为调用方常常还要用到
     * {@code realName} 等其他字段，返回实体比返回 id 少一次查询。
     * 实体只在服务层内部流转，不会出现在响应体里。
     */
    Expert requireApprovedExpert(Long userId);
}
