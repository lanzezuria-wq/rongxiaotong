package com.rxt.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.PageResult;
import com.rxt.common.ResultCode;
import com.rxt.common.exception.BizException;
import com.rxt.entity.User;
import com.rxt.mapper.UserMapper;
import com.rxt.trade.constant.OrderConst;
import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.dto.OrderCreateDTO;
import com.rxt.trade.dto.OrderShipDTO;
import com.rxt.trade.entity.Address;
import com.rxt.trade.entity.Cart;
import com.rxt.trade.entity.Order;
import com.rxt.trade.entity.OrderItem;
import com.rxt.trade.entity.Product;
import com.rxt.trade.mapper.AddressMapper;
import com.rxt.trade.mapper.CartMapper;
import com.rxt.trade.mapper.OrderItemMapper;
import com.rxt.trade.mapper.OrderMapper;
import com.rxt.trade.mapper.ProductMapper;
import com.rxt.trade.service.OrderService;
import com.rxt.trade.util.TradeJsonUtil;
import com.rxt.trade.vo.OrderDetailVO;
import com.rxt.trade.vo.OrderVO;
import com.rxt.trade.vo.ProductDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务实现 —— P2 里逻辑最密的类。
 *
 * <p>三条贯穿全文的原则：
 *
 * <p><b>1. 状态变更一律用"带条件的 UPDATE"，不用"先读再写"。</b>
 * <pre>
 *   ✅ update ... where id = ? and status = 0    ← 靠数据库行锁保证只有一个请求成功
 *   ❌ order.setStatus(1); updateById(order)     ← 并发下两个请求都能通过前置校验
 * </pre>
 * 前者受影响行数为 0 就说明"状态已经被别人改了"，这时抛异常让前端刷新，
 * 而不是盲目覆盖。
 *
 * <p><b>2. 归属校验必须在 Service 里做。</b>角色权限只保证"是农户/是买家"，
 * 保证不了"是这一单的农户/买家"。
 *
 * <p><b>3. 涉及库存的操作必须在一个事务里。</b>扣库存 + 写订单 + 写明细，
 * 任何一步失败都要整体回滚，否则会出现"库存扣了但订单没生成"的账目错乱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    private final AddressMapper addressMapper;
    private final UserMapper userMapper;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    /** 角色：买家视角 / 卖家视角 */
    private static final String ROLE_BUYER = "buyer";
    private static final String ROLE_FARMER = "farmer";

    /** 订单号里的时间部分格式 */
    private static final DateTimeFormatter ORDER_NO_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 单次超时清理最多处理的订单数，避免一次请求扫太多行 */
    private static final int EXPIRE_CLEAN_LIMIT = 100;

    // ==========================================================================
    // 创建订单
    // ==========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long buyerId, OrderCreateDTO dto) {
        List<Cart> carts = loadOwnedCarts(buyerId, dto.getCartIds());

        // 收货地址必须是自己的
        Address address = addressMapper.selectById(dto.getAddressId());
        if (address == null || !buyerId.equals(address.getUserId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "收货地址不存在，请重新选择");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>(carts.size());
        Long farmerId = null;

        for (Cart cart : carts) {
            // ⚠️ 关键：这里用 FOR UPDATE 加行锁，不是普通的 selectById。
            // 不加锁的话，"库存剩 1 件、两个人同时下单"会让两人都通过校验，
            // 库存被扣成 -1（超卖）。
            Product product = productMapper.selectByIdForUpdate(cart.getProductId());
            if (product == null || isDeleted(product)) {
                throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "购物车里存在已下架的商品，请先移除");
            }
            if (!isOnSale(product)) {
                throw new BizException(ResultCode.PRODUCT_OFF_SHELF,
                        "商品「" + product.getName() + "」已下架，请先移除");
            }
            if (product.getStock() == null || product.getStock() < cart.getQuantity()) {
                throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                        "商品「" + product.getName() + "」库存不足，当前仅剩 "
                                + (product.getStock() == null ? 0 : product.getStock()) + " 件");
            }

            // 一件商品只能属于一个卖家，所以运费/发货口径是唯一的。
            // 购物车里混了多个农户的商品时就无法用一个订单表达了。
            if (farmerId == null) {
                farmerId = product.getFarmerId();
            } else if (!farmerId.equals(product.getFarmerId())) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "一次只能结算同一个卖家的商品，请分开下单");
            }

            // 扣库存
            product.setStock(product.getStock() - cart.getQuantity());
            productMapper.updateById(product);

            BigDecimal price = resolvePrice(product, cart.getSkuSpec());
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(cart.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            // 快照：商品名和成交价都冻结在这一刻，之后卖家改名/调价不影响这一单
            item.setProductName(product.getName());
            item.setSkuSpec(cart.getSkuSpec() == null || cart.getSkuSpec().isBlank()
                    ? null : cart.getSkuSpec());
            item.setPrice(price);
            item.setQuantity(cart.getQuantity());
            item.setSubtotal(subtotal);
            items.add(item);
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setBuyerId(buyerId);
        order.setFarmerId(farmerId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderConst.STATUS_UNPAID);
        order.setAddressId(address.getId());
        // 存"绝对时间"而不是"15 分钟后"：判断超时就能写成 expire_at < now()，
        // 一条 SQL 批量扫，不用在应用层给每行做时间加法。
        order.setExpireAt(LocalDateTime.now().plusMinutes(OrderConst.EXPIRE_MINUTES));
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 下单成功后清掉这些购物车项，否则用户回到购物车还看得见，容易重复下单
        cartMapper.deleteByIds(carts.stream().map(Cart::getId).collect(Collectors.toList()));

        OrderVO vo = OrderVO.from(order);
        vo.setItemCount(items.size());
        vo.setFarmerName(nameOf(farmerId));
        vo.setBuyerName(nameOf(buyerId));
        return vo;
    }

    // ==========================================================================
    // 列表 / 详情
    // ==========================================================================

    @Override
    public PageResult<OrderVO> list(Long userId, String role, Integer status, Integer page, Integer size) {
        // 先把超时的待付款订单批量取消掉（含回滚库存），
        // 这样用户在列表里看到的状态永远准确，不会出现"早就过期了还显示待付款"。
        cancelExpiredOrders();

        long current = (page == null || page < 1) ? DEFAULT_PAGE : page;
        long pageSize = (size == null || size < 1) ? DEFAULT_SIZE
                : Math.min(size, OrderConst.MAX_PAGE_SIZE);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (ROLE_FARMER.equalsIgnoreCase(role)) {
            wrapper.eq(Order::getFarmerId, userId);
        } else if (ROLE_BUYER.equalsIgnoreCase(role)) {
            wrapper.eq(Order::getBuyerId, userId);
        } else {
            // role 是必传的。不传时不能默认"两边都查"——那样分页语义含糊，
            // 而且会把"我作为卖家"和"我作为买家"的订单混在一页里。
            throw new BizException(ResultCode.PARAM_ERROR, "role 参数必须是 buyer 或 farmer");
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        IPage<Order> result = orderMapper.selectPage(new Page<>(current, pageSize), wrapper);

        List<OrderVO> vos = result.getRecords().stream().map(OrderVO::from).collect(Collectors.toList());
        // 列表页要显示"共 N 件"和买卖双方姓名，批量补齐，避免 N+1
        fillItemCounts(vos);
        fillUserNames(vos);

        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public OrderDetailVO detail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
        // 只有这一单的买家或卖家能看
        if (!Objects.equals(order.getBuyerId(), userId) && !Objects.equals(order.getFarmerId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该订单");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        OrderDetailVO vo = OrderDetailVO.from(order, items);
        vo.setItemCount(items.size());
        vo.setBuyerName(nameOf(order.getBuyerId()));
        vo.setFarmerName(nameOf(order.getFarmerId()));
        vo.setAddress(buildAddressText(order.getAddressId()));
        return vo;
    }

    // ==========================================================================
    // 状态流转
    // ==========================================================================

    @Override
    public void pay(Long buyerId, Long orderId) {
        Order order = requireBuyer(buyerId, orderId);

        // 条件更新：只有当前状态真的是"待付款"，这次更新才会命中。
        // 两个人同时点支付，只有一个能拿到 rows = 1，另一个会走到下面的异常分支。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, OrderConst.STATUS_UNPAID)
                .set(Order::getStatus, OrderConst.STATUS_UNSHIPPED)
                .set(Order::getPaidAt, LocalDateTime.now()));

        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR, "订单状态已变更，请刷新后重试");
        }
    }

    @Override
    public void ship(Long farmerId, Long orderId, OrderShipDTO dto) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
        // 发货权限只属于这一单的卖家。角色权限挡不住这里——两个农户角色相同
        if (!Objects.equals(order.getFarmerId(), farmerId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能对自己店铺的订单发货");
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderConst.STATUS_UNSHIPPED)
                .set(Order::getStatus, OrderConst.STATUS_SHIPPED)
                .set(Order::getExpressCompany, dto.getExpressCompany())
                .set(Order::getExpressNo, dto.getExpressNo())
                .set(Order::getShippedAt, LocalDateTime.now()));

        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR, "只有「待发货」的订单可以发货");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long buyerId, Long orderId) {
        Order order = requireBuyer(buyerId, orderId);

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderConst.STATUS_SHIPPED)
                .set(Order::getStatus, OrderConst.STATUS_COMPLETED)
                .set(Order::getReceivedAt, LocalDateTime.now()));

        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR, "只有「已发货」的订单可以确认收货");
        }

        // 确认收货才算交易完成，这时才累加商品销量。
        // 放在"收货"而不是"支付"是因为销量代表"实际成交"，
        // 支付后还可能被取消（虽然本项目只允许取消未付款的订单）。
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("sales_count = sales_count + " + item.getQuantity()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long buyerId, Long orderId) {
        Order order = requireBuyer(buyerId, orderId);

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderConst.STATUS_UNPAID)
                .set(Order::getStatus, OrderConst.STATUS_CANCELLED)
                .set(Order::getCancelledAt, LocalDateTime.now()));

        if (rows == 0) {
            // 已付款的订单不能直接取消——那需要退款流程，本项目不实现。
            // 静默成功会让用户以为取消了，实际钱已付、货会照发。
            throw new BizException(ResultCode.ORDER_STATUS_ERROR,
                    "只有「待付款」的订单可以取消，已付款订单请联系客服");
        }

        restoreStock(orderId);
    }

    // ==========================================================================
    // 私有辅助
    // ==========================================================================

    /**
     * 把超过 15 分钟仍未支付的订单批量取消并回滚库存。
     *
     * <p><b>为什么不用 {@code @Scheduled} 定时任务？</b>
     * 演示项目里定时任务有两个麻烦：一是要额外开线程池配置，
     * 二是任务执行时机不可控（刚过期但还没扫到，用户看到的状态就是错的）。
     * "查列表时顺手扫一遍"的做法，保证用户每次看到的都是准确状态，
     * 而且实现只是一条 SQL + 一个循环。
     *
     * <p>整个清理过程<b>不做大事务</b>：每条订单独立处理，一条失败不影响其他条。
     * 因为这些是"本来就已经过期"的数据，没必要因为它们失败而回滚用户看列表这件事。
     */
    private void cancelExpiredOrders() {
        List<Order> expired = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderConst.STATUS_UNPAID)
                        .isNotNull(Order::getExpireAt)
                        .lt(Order::getExpireAt, LocalDateTime.now())
                        .last("LIMIT " + EXPIRE_CLEAN_LIMIT));

        for (Order order : expired) {
            try {
                // 同样用条件更新，避免和"用户此刻正好在支付"打架
                int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, order.getId())
                        .eq(Order::getStatus, OrderConst.STATUS_UNPAID)
                        .set(Order::getStatus, OrderConst.STATUS_CANCELLED)
                        .set(Order::getCancelledAt, LocalDateTime.now()));
                if (rows > 0) {
                    restoreStock(order.getId());
                    log.info("订单 {} 超过 {} 分钟未支付，已自动取消并回滚库存",
                            order.getOrderNo(), OrderConst.EXPIRE_MINUTES);
                }
            } catch (Exception e) {
                // 单条失败不影响其他条，也不影响列表返回
                log.warn("取消超时订单 {} 失败", order.getOrderNo(), e);
            }
        }
    }

    /** 取消订单时把库存还回去。用 setSql 让数据库原子自增，避免并发丢更新 */
    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, item.getProductId())
                    .setSql("stock = stock + " + item.getQuantity()));
        }
    }

    /**
     * 取出购物车项并校验都归当前用户所有。
     *
     * <p>数量核对那行很重要：如果前端传了 3 个 id 但只能查到 2 条，
     * 说明有个 id 是别人的或者已经被删了——这种情况要整体拒绝，
     * 不能"能查到几条就结算几条"，否则用户以为买了 3 件实际只买到 2 件。
     */
    private List<Cart> loadOwnedCarts(Long buyerId, List<Long> cartIds) {
        Set<Long> uniqueIds = new HashSet<>(cartIds);
        List<Cart> carts = cartMapper.selectByIds(uniqueIds);
        if (carts.isEmpty()) {
            throw new BizException(ResultCode.CART_EMPTY, "请先选择要购买的商品");
        }
        if (carts.size() != uniqueIds.size()) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "部分购物车项已失效，请刷新后重试");
        }
        for (Cart cart : carts) {
            if (!buyerId.equals(cart.getUserId())) {
                throw new BizException(ResultCode.FORBIDDEN, "购物车项不存在或无权操作");
            }
        }
        return carts;
    }

    /** 查订单并确认买家是本人才放行 */
    private Order requireBuyer(Long buyerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
        if (!Objects.equals(order.getBuyerId(), buyerId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己购买的订单");
        }
        return order;
    }

    /**
     * 生成订单号：{@code RXT + yyyyMMddHHmmss + 6 位随机}。
     *
     * <p>{@code Random} 用 {@code ThreadLocalRandom} 而不是 {@code Math.random()}：
     * 前者在多线程下不会争抢同一个种子，性能更好。
     * 表上有唯一索引兜底，万一同一秒内随机数撞了，插入会失败而不是产生重复订单号。
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMAT);
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 1000000);
        return OrderConst.ORDER_NO_PREFIX + timestamp + random;
    }

    /** 取规格价，没有规格就用商品基础价。与 CartVO 的逻辑保持一致 */
    private BigDecimal resolvePrice(Product product, String skuSpec) {
        if (skuSpec != null && !skuSpec.isBlank()) {
            List<ProductDetailVO.SkuVO> skus =
                    TradeJsonUtil.parseList(product.getSkuList(), ProductDetailVO.SkuVO.class);
            for (ProductDetailVO.SkuVO sku : skus) {
                if (skuSpec.equals(sku.getSpec()) && sku.getPrice() != null) {
                    return sku.getPrice();
                }
            }
        }
        return product.getPrice();
    }

    /** 批量填 itemCount（"共 N 件"），一次查询搞定，避免每行查一次 */
    private void fillItemCounts(List<OrderVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> orderIds = vos.stream().map(OrderVO::getId).collect(Collectors.toList());
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, Long> countMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));
        vos.forEach(vo -> vo.setItemCount(
                countMap.getOrDefault(vo.getId(), 0L).intValue()));
    }

    /** 批量填买卖双方姓名 */
    private void fillUserNames(List<OrderVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        vos.forEach(vo -> {
            if (vo.getBuyerId() != null) {
                userIds.add(vo.getBuyerId());
            }
            if (vo.getFarmerId() != null) {
                userIds.add(vo.getFarmerId());
            }
        });
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getName() == null ? "" : u.getName(),
                        (a, b) -> a));
        vos.forEach(vo -> {
            vo.setBuyerName(nameMap.get(vo.getBuyerId()));
            vo.setFarmerName(nameMap.get(vo.getFarmerId()));
        });
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getName();
    }

    /** 拼收货信息文本给订单详情用 */
    private String buildAddressText(Long addressId) {
        if (addressId == null) {
            return null;
        }
        Address address = addressMapper.selectById(addressId);
        if (address == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(address.getReceiver() == null ? "" : address.getReceiver());
        if (address.getPhone() != null) {
            sb.append(' ').append(address.getPhone());
        }
        sb.append(' ');
        // 省市区为空时跳过，避免拼出多余空格
        appendIfPresent(sb, address.getProvince());
        appendIfPresent(sb, address.getCity());
        appendIfPresent(sb, address.getDistrict());
        appendIfPresent(sb, address.getDetail());
        return sb.toString().trim();
    }

    private void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value);
        }
    }

    private boolean isDeleted(Product product) {
        return product.getStatus() != null && product.getStatus() == ProductConst.STATUS_DELETED;
    }

    private boolean isOnSale(Product product) {
        return product.getStatus() != null && product.getStatus() == ProductConst.STATUS_ON_SALE;
    }
}
