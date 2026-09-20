package com.rxt.trade.service;

import com.rxt.common.PageResult;
import com.rxt.trade.dto.OrderCreateDTO;
import com.rxt.trade.dto.OrderShipDTO;
import com.rxt.trade.vo.OrderDetailVO;
import com.rxt.trade.vo.OrderVO;

/**
 * 订单服务 —— M3 订单 7 个接口。<b>整个 P2 里业务最重、最容易出错的部分。</b>
 *
 * <p>状态机见 {@link com.rxt.trade.constant.OrderConst}：
 * <pre>
 *   0 待付款 ──支付──→ 1 待发货 ──发货──→ 2 已发货 ──收货──→ 3 已完成
 *      │
 *      └──取消 / 15 分钟超时──→ 4 已取消
 * </pre>
 *
 * <p><b>每个变更状态的方法都要做三重校验，缺一不可：</b>
 * <ol>
 *   <li><b>状态合法</b>——当前状态是否允许这个动作（"未付款就发货"要拦）。
 *       不合法抛 {@code 2002}；</li>
 *   <li><b>身份归属</b>——发货必须订单上的 {@code farmerId} 本人，
 *       收货/取消必须 {@code buyerId} 本人。不匹配抛 {@code 1007}。
 *       <b>角色权限挡不住这一层</b>（两个农户角色相同）；</li>
 *   <li><b>数据存在</b>——订单查不到抛 {@code 1008}。</li>
 * </ol>
 * 另外所有状态变更都用<b>带条件的 UPDATE</b>（{@code WHERE id = ? AND status = ?}），
 * 靠数据库的行锁保证并发下只有一个请求能改成功——两个人同时点"支付"不会重复扣款。
 */
public interface OrderService {

    /**
     * 创建订单（从购物车下单）。
     *
     * <p><b>整个过程必须在一个事务里</b>，因为它做了四件事：
     * <ol>
     *   <li>校验购物车项都属于当前用户、不为空；</li>
     *   <li>逐项校验商品在售 + 库存足够，并按 {@code SELECT ... FOR UPDATE} 锁行扣减库存；</li>
     *   <li>写 t_order（状态 0 待付款，{@code expireAt = now + 15 分钟}）+ t_order_item（存商品名/价格快照）；</li>
     *   <li>删除已下单的购物车项。</li>
     * </ol>
     * 任何一步失败全部回滚，否则会出现"库存扣了但订单没建"这种对不上账的情况。
     *
     * <p><b>15 分钟超时用"下单时算好绝对时间"而不是定时任务</b>：
     * 演示项目不必上 {@code @Scheduled} 或 Redis 过期键，
     * 把 {@code expire_at} 存成绝对时间，查列表时顺手把过期的置为已取消即可。
     *
     * @return 新建的订单（前端需要 orderNo 和 id 去跳支付页）
     */
    OrderVO create(Long buyerId, OrderCreateDTO dto);

    /**
     * 订单列表。
     *
     * <p>{@code role} 决定查哪一边：
     * <ul>
     *   <li>{@code buyer} → {@code buyer_id = 当前用户}（我买的）</li>
     *   <li>{@code farmer} → {@code farmer_id = 当前用户}（我卖的）</li>
     * </ul>
     * 必须传，不允许"两边混在一起返回"——那样前端没法分页，语义也含糊。
     *
     * <p>查询前会先执行一次<b>超时订单清理</b>（把 {@code expire_at < now() 且 status = 0}
     * 的置为已取消并回滚库存），这样用户在列表里看到的永远是准确状态。
     */
    PageResult<OrderVO> list(Long userId, String role, Integer status, Integer page, Integer size);

    /** 订单详情（含商品明细）。买家、卖家都能看，其他人抛 1007 */
    OrderDetailVO detail(Long userId, Long orderId);

    /**
     * Mock 支付：待付款 → 待发货，记录 {@code paidAt}。
     *
     * <p>演示环境没有真实支付渠道，直接置状态。只有 {@code buyerId} 本人能付。
     */
    void pay(Long buyerId, Long orderId);

    /** 卖家发货：待发货 → 已发货，记录快递单号。只有 {@code farmerId} 本人能发 */
    void ship(Long farmerId, Long orderId, OrderShipDTO dto);

    /** 买家确认收货：已发货 → 已完成。同时累加商品的 {@code salesCount} */
    void receive(Long buyerId, Long orderId);

    /**
     * 取消订单：待付款 → 已取消，并且<b>把库存还回去</b>。
     *
     * <p>只有待付款能取消。已付款的订单要走退款流程，本项目不做，
     * 所以"已付款后想取消"会返回 2002 而不是静默成功。
     */
    void cancel(Long buyerId, Long orderId);
}
