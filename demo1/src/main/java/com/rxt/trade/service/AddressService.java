package com.rxt.trade.service;

import com.rxt.trade.dto.AddressDTO;
import com.rxt.trade.vo.AddressVO;

import java.util.List;

/**
 * 收货地址服务 —— M3 收货地址 4 个接口。
 *
 * <p><b>这个模块的核心约束是"同一用户只能有一条默认地址"</b>，
 * 而这条约束<b>数据库没法保证</b>：不能对 {@code is_default} 建唯一索引，
 * 因为一个用户会有多条 {@code is_default = 0} 的记录，唯一索引会把它们全挡掉。
 * 所以只能靠 Service 层的"先清后设"两步操作，且必须在<b>同一个事务</b>里。
 */
public interface AddressService {

    /**
     * 地址列表。
     *
     * <p>排序规则：<b>默认地址永远排第一</b>，其余按创建时间倒序。
     * 这样前端不用自己排序，下单页的地址选择器默认就选中第一个。
     */
    List<AddressVO> list(Long userId);

    /**
     * 新增地址。
     *
     * <p>如果传了 {@code isDefault = 1}，会先把该用户其他地址的默认标记清掉。
     * 另外：<b>用户的第一个地址自动成为默认</b>——否则新用户加完地址
     * 下单时还得再手动勾一次"设为默认"，多一步无意义的操作。
     *
     * @return 新地址 id
     */
    Long create(Long userId, AddressDTO dto);

    /** 编辑地址。归属校验 + 同样的"默认地址唯一"处理 */
    void update(Long userId, Long id, AddressDTO dto);

    /**
     * 删除地址。
     *
     * <p>如果删掉的正好是默认地址，会把剩下最早创建的那条设为默认，
     * 避免出现"一个默认地址都没有"的状态。
     */
    void delete(Long userId, Long id);
}
