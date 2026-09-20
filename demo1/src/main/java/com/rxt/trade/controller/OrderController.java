package com.rxt.trade.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.security.SecurityUtils;
import com.rxt.trade.dto.OrderCreateDTO;
import com.rxt.trade.dto.OrderPayDTO;
import com.rxt.trade.dto.OrderShipDTO;
import com.rxt.trade.service.OrderService;
import com.rxt.trade.vo.OrderDetailVO;
import com.rxt.trade.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口 —— M3 的 7 个订单接口（契约路径 {@code /api/order/*}）。
 * <b>整个 P2 里最需要注意权限的一组接口。</b>
 *
 * <p><b>同一个 {@code /api/order/list} 被两种角色共用</b>，
 * 靠必传的 {@code role} 参数区分"我买的"和"我卖的"。
 * 这个设计的好处是一个接口满足两端需求；
 * 风险是如果 role 传错了，用户会看到空白列表（而不是别人的数据）——
 * 因为查询条件始终绑定了当前用户 id，最坏情况只是查不到，不会越权。
 *
 * <p><b>注意 {@code GET /list} 和 {@code GET /{id}} 的路径冲突问题：</b>
 * {@code /list} 是字面量，{@code /{id}} 是占位符，Spring 优先匹配字面量，
 * 所以 {@code GET /api/order/list} 不会走进 {@code detail(id = "list")}
 * 导致类型转换报错。
 *
 * <p>所有状态变更接口都做了<b>归属校验 + 状态机校验</b>，
 * 具体规则见 {@link com.rxt.trade.service.impl.OrderServiceImpl}。
 */
@Tag(name = "06. M3 订单", description = "创建/列表/详情/支付/发货/收货/取消（7 个接口）")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单",
            description = "从购物车结算。传购物车项 id 列表和收货地址 id。"
                    + "会校验库存并扣减、生成订单和明细、清空已下单的购物车项，全在一个事务里。"
                    + "订单 15 分钟未支付会自动取消并回滚库存。")
    @PostMapping("/create")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok("下单成功，请尽快支付", orderService.create(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "订单列表",
            description = "role 必传：buyer 查我买的，farmer 查我卖的。可按状态筛选、分页。"
                    + "每次查询前会先清理超时未支付的订单，所以看到的状态一定是准确的。")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(
            @RequestParam String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(orderService.list(SecurityUtils.currentUserId(), role, status, page, size));
    }

    @Operation(summary = "订单详情",
            description = "含商品明细和收货信息。只有这一单的买家或卖家能看，其他人返回 1007。")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "Mock 支付",
            description = "演示环境没有真实支付渠道，直接置为待发货并记录支付时间。"
                    + "只有买家本人能支付，只有「待付款」状态可支付。"
                    + "⚠️ 正式项目必须由支付网关异步回调确认，不能信任此接口。")
    @PostMapping("/pay")
    public Result<Void> pay(@Valid @RequestBody OrderPayDTO dto) {
        orderService.pay(SecurityUtils.currentUserId(), dto.getOrderId());
        return Result.ok("支付成功", null);
    }

    @Operation(summary = "卖家发货",
            description = "只有这一单的卖家本人能操作（角色权限挡不住同角色越权，Service 里比对了 farmerId）。"
                    + "只有「待发货」状态可发货。需填写快递公司和单号。")
    @PutMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @Valid @RequestBody OrderShipDTO dto) {
        orderService.ship(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("发货成功", null);
    }

    @Operation(summary = "买家确认收货",
            description = "只有买家本人能操作。只有「已发货」状态可确认。"
                    + "确认后订单完成，同时累加对应商品的销量。")
    @PutMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        orderService.receive(SecurityUtils.currentUserId(), id);
        return Result.ok("已确认收货", null);
    }

    @Operation(summary = "取消订单",
            description = "只有买家本人能操作，且只有「待付款」状态可取消。"
                    + "取消后会把库存还回商品。已付款订单不走此接口（需要退款流程，本项目未实现）。")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(SecurityUtils.currentUserId(), id);
        return Result.ok("订单已取消", null);
    }
}
