package com.rxt.trade.controller;

import com.rxt.common.Result;
import com.rxt.security.SecurityUtils;
import com.rxt.trade.dto.AddressDTO;
import com.rxt.trade.service.AddressService;
import com.rxt.trade.vo.AddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口 —— M3 的 4 个地址接口（契约路径 {@code /api/address/*}）。
 *
 * <p>全部要求登录。列表返回时<b>默认地址排第一</b>，
 * 前端下单页的地址选择器可以直接选中第一项，不用自己排序。
 *
 * <p><b>关于"默认地址唯一"这件事：</b>数据库层面无法约束
 * （不能给 is_default 建唯一索引，因为一个用户有多条 is_default = 0 的记录），
 * 所以完全依赖 Service 层在一个事务里"先清后设"。
 * 详见 {@link com.rxt.trade.service.impl.AddressServiceImpl}。
 */
@Tag(name = "05. M3 收货地址", description = "列表/新增/编辑/删除（4 个接口）")
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "地址列表",
            description = "默认地址排第一，其余按创建时间正序。含拼好的 fullAddress 字段。")
    @GetMapping("/list")
    public Result<List<AddressVO>> list() {
        return Result.ok(addressService.list(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "新增地址",
            description = "用户的第一个地址会自动成为默认。isDefault 传 1 时会把其他地址的默认标记清掉。")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AddressDTO dto) {
        return Result.ok("地址已保存", addressService.create(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "编辑地址",
            description = "只能改自己的地址。设为默认时会同步清掉其他地址的默认标记。")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressDTO dto) {
        addressService.update(SecurityUtils.currentUserId(), id, dto);
        return Result.ok("地址已更新", null);
    }

    @Operation(summary = "删除地址",
            description = "删除默认地址后，会自动把剩下最早创建的那条设为默认，"
                    + "避免出现「一个默认地址都没有」导致下单页选不中地址。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(SecurityUtils.currentUserId(), id);
        return Result.ok("地址已删除", null);
    }
}
