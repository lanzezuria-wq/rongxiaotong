package com.rxt.trade.service;

import com.rxt.common.PageResult;
import com.rxt.trade.dto.ProductDTO;
import com.rxt.trade.vo.ProductDetailVO;
import com.rxt.trade.vo.ProductVO;

/**
 * 商品服务 —— M3 商品 CRUD 6 个接口的业务入口。
 *
 * <p><b>签名里的两条纪律（和 P1 的 UserService 完全一致）：</b>
 * <ol>
 *   <li><b>不返回 {@code Result}</b>，只返回 VO 或 void。统一响应是 Controller 的事。
 *       一旦 Service 开始 return Result，事务回滚就失效了（抛异常才回滚）；</li>
 *   <li><b>不读 {@code SecurityContextHolder}</b>。"当前用户是谁"由 Controller
 *       从 Token 解出来当参数传进来。这样 Service 能脱离 Web 环境单测，
 *       而且"操作的到底是谁"在方法签名上就看得见。</li>
 * </ol>
 *
 * <p>失败一律抛 {@code BizException}，由全局异常处理器翻译。
 */
public interface ProductService {

    /**
     * 商品列表（分页）。
     *
     * <p>游客也能调（SecurityConfig 里放行了 GET /api/product/**）。
     * 所以这里<b>只能查在售商品</b>——把下架和已删除的过滤掉，
     * 否则未登录用户能通过列表看到农户下架的商品。
     *
     * @param keyword      商品名模糊匹配，可为空
     * @param categoryCode 分类编码，可为空
     * @param sort         排序：{@code default} 综合 / {@code price_asc} / {@code price_desc}
     *                     / {@code sales} 销量降序 / {@code newest} 最新。为空按综合
     * @param page         页码，从 1 开始
     * @param size         每页条数
     */
    PageResult<ProductVO> list(String keyword, String categoryCode, String sort, Integer page, Integer size);

    /**
     * 商品详情。
     *
     * <p>副作用：浏览量 +1。这是本模块唯一的"GET 请求写库"场景，
     * 演示项目可接受；正式项目应该改成异步或 Redis 计数，避免读接口有写锁。
     *
     * <p>商品不存在或已删除 → 抛 1008（资源不存在）。
     * 已下架的商品<b>仍然可以看详情</b>——用户可能从收藏夹点进来，
     * 直接报"不存在"体验更差。
     */
    ProductDetailVO detail(Long id);

    /**
     * 发布商品。
     *
     * @param farmerId 发布者，由 Controller 从 Token 取
     * @param dto      商品信息
     * @return 新商品 id
     */
    Long create(Long farmerId, ProductDTO dto);

    /**
     * 编辑商品（整体覆盖语义，前端传完整表单）。
     *
     * <p>⚠️ <b>必须校验归属</b>：只有商品的 {@code farmerId} 本人才有权改。
     * 光靠 {@code hasRole("farmer")} 挡不住——农户 A 和农户 B 角色相同，
     * 不校验的话 A 能改 B 的商品。越权时抛 1007。
     */
    void update(Long farmerId, Long id, ProductDTO dto);

    /**
     * 上架 / 下架。
     *
     * @param status 只能是 0（下架）或 1（上架）；删除走 {@link #delete}，不能通过改状态删
     */
    void updateStatus(Long farmerId, Long id, Integer status);

    /**
     * 删除商品（软删除，置 status = 2）。
     *
     * <p>软删而不是物理删：订单明细里存着 productId，物理删会让历史订单
     * 查不到商品来源。同理，这里也<b>不校验是否有未完成订单</b>，
     * 因为软删之后订单照常能展示（明细存了商品名快照）。
     */
    void delete(Long farmerId, Long id);
}
