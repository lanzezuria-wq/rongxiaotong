package com.rxt.trade.service;

import com.rxt.trade.vo.ProductVO;

import java.util.List;
import java.util.Map;

/**
 * 首页服务 —— M2 首页 4 个接口。
 *
 * <p><b>为什么这几个方法返回 {@code Map} 而不是专门的 VO？</b>
 * 首页是典型的"聚合展示"接口：轮播图字段来自字典表、统计数据是 Mock 的、
 * 文章列表还给 P3/P4 管。给它们各造一个 VO 类，收益只是字段名类型安全，
 * 代价是多 4 个几乎不会复用的类。演示项目的取舍是先用 Map，
 * 如果后续这几个结构稳定了再抽 VO。
 *
 * <p>唯一例外是 {@link #recommended()}——它返回的是完整的商品对象，
 * 复用 {@link ProductVO}，前端可以直接拿现成的商品卡片组件渲染。
 *
 * <p>这 4 个接口<b>全部对游客开放</b>（SecurityConfig 里放行了 GET /api/home/**），
 * 所以绝对不能返回任何敏感信息。
 */
public interface HomeService {

    /**
     * 轮播图。
     *
     * <p>数据来源是字典表 {@code type = banner} 的项：{@code name} 当标题，
     * {@code remark} 存图片 URL。这样运营改轮播图不用动代码，改字典就行。
     *
     * <p>字典里没有 banner 项时返回空列表，前端应该自己兜底显示默认图，
     * 不要给个 404 让首页开天窗。
     */
    List<Map<String, Object>> banners();

    /**
     * 推荐商品：在售商品里<b>随机取 12 个</b>。
     *
     * <p>用 {@code ORDER BY RAND()} 实现。这个写法在数据量大时会很慢
     * （全表扫描 + 排序），演示数据量（十几个商品）完全无所谓，
     * 但要清楚它不能直接上生产。
     */
    List<ProductVO> recommended();

    /**
     * 首页热门文章 Top 5（按浏览量倒序）。
     *
     * <p>文章表是 P1 建的、归 P3/P4 管（M5 专家模块）。首页这里只读，
     * 而且<b>只返回已发布的</b>（{@code status = 1}），待审核和已拒绝的不能露出来。
     */
    List<Map<String, Object>> articles();

    /**
     * 首页统计数字：商品总数 / 融资成功数 / 用户数。
     *
     * <p>需求里这就是<b> Mock 数据</b>，直接返回固定数字即可，
     * 不用真去 count——演示时数字好看比数字真实重要，
     * 而且避免首页每次都打三个 count 查询。
     */
    Map<String, Object> stats();
}
