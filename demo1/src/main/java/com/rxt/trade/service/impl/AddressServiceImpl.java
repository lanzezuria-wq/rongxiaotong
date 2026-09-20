package com.rxt.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.common.ResultCode;
import com.rxt.common.exception.BizException;
import com.rxt.trade.dto.AddressDTO;
import com.rxt.trade.entity.Address;
import com.rxt.trade.mapper.AddressMapper;
import com.rxt.trade.service.AddressService;
import com.rxt.trade.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现。
 *
 * <p><b>全类最需要注意的一点：涉及"默认地址"的写操作都必须带
 * {@code @Transactional}。</b>因为"清掉旧默认 + 设新默认"是两次 UPDATE，
 * 中间失败会留下"零个默认地址"或"两个默认地址"的脏状态。
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    private static final int DEFAULT_NO = 0;
    private static final int DEFAULT_YES = 1;

    /** 单用户地址数量上限，防止刷数据。演示够用 */
    private static final int MAX_ADDRESS = 20;

    @Override
    public List<AddressVO> list(Long userId) {
        // 排序策略：默认地址永远第一（isDefault 降序），其余按创建时间正序。
        // 这样下单页的地址选择器不用做任何排序，第一项天然就是用户想要的。
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByAsc(Address::getCreateTime));
        return list.stream().map(AddressVO::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, AddressDTO dto) {
        Long count = addressMapper.selectCount(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId));
        if (count != null && count >= MAX_ADDRESS) {
            throw new BizException(ResultCode.PARAM_ERROR, "收货地址最多 " + MAX_ADDRESS + " 条");
        }

        Address entity = new Address();
        copyFields(dto, entity);
        entity.setUserId(userId);

        // 用户的第一个地址自动成为默认——否则新用户加完地址还要手动勾一次，
        // 下单时才发现没有默认地址。
        boolean asDefault = wantDefault(dto) || count == null || count == 0;
        entity.setIsDefault(asDefault ? DEFAULT_YES : DEFAULT_NO);

        if (asDefault) {
            clearDefault(userId);
        }
        addressMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long id, AddressDTO dto) {
        Address existing = requireOwned(userId, id);
        // 这个标记要在 clearDefault 之前算好，因为 clearDefault 会影响 isDefault 的读取
        boolean asDefault = wantDefault(dto);

        copyFields(dto, existing);
        existing.setIsDefault(asDefault ? DEFAULT_YES : DEFAULT_NO);

        if (asDefault) {
            clearDefault(userId);
        }
        addressMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        Address existing = requireOwned(userId, id);
        addressMapper.deleteById(id);

        // 删掉的正好是默认地址 → 从剩下的里补一个出来，
        // 避免出现"一个默认地址都没有"的状态（下单页会选不中任何地址）。
        if (existing.getIsDefault() != null && existing.getIsDefault() == DEFAULT_YES) {
            Address fallback = addressMapper.selectOne(
                    new LambdaQueryWrapper<Address>()
                            .eq(Address::getUserId, userId)
                            .orderByAsc(Address::getCreateTime)
                            .last("LIMIT 1"));
            if (fallback != null) {
                addressMapper.update(null,
                        new LambdaUpdateWrapper<Address>()
                                .eq(Address::getId, fallback.getId())
                                .set(Address::getIsDefault, DEFAULT_YES));
            }
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 查出地址并校验归属。
     *
     * <p>地址 id 是自增的，不校验的话用户改一下 id 就能编辑/删除别人的地址。
     * 查不到和不属于自己<b>返回同一个错误</b>（1007 权限不足），
     * 不区分"不存在"和"不是你的"——否则攻击者能靠错误码差异枚举出哪些 id 真实存在。
     */
    private Address requireOwned(Long userId, Long id) {
        Address address = addressMapper.selectById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "地址不存在或无权操作");
        }
        return address;
    }

    /** 把该用户其他地址的默认标记清掉 */
    private void clearDefault(Long userId) {
        addressMapper.update(null,
                new LambdaUpdateWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, DEFAULT_YES)
                        .set(Address::getIsDefault, DEFAULT_NO));
    }

    private boolean wantDefault(AddressDTO dto) {
        return dto.getIsDefault() != null && dto.getIsDefault() == DEFAULT_YES;
    }

    private void copyFields(AddressDTO dto, Address entity) {
        entity.setReceiver(dto.getReceiver());
        entity.setPhone(dto.getPhone());
        entity.setProvince(dto.getProvince());
        entity.setCity(dto.getCity());
        entity.setDistrict(dto.getDistrict());
        entity.setDetail(dto.getDetail());
    }
}
