package com.rxt.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rxt.common.PageResult;
import com.rxt.common.ResultCode;
import com.rxt.common.exception.BizException;
import com.rxt.entity.DictItem;
import com.rxt.entity.User;
import com.rxt.mapper.DictItemMapper;
import com.rxt.mapper.UserMapper;
import com.rxt.trade.constant.OrderConst;
import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.dto.ProductDTO;
import com.rxt.trade.entity.Product;
import com.rxt.trade.mapper.ProductMapper;
import com.rxt.trade.service.ProductService;
import com.rxt.trade.util.TradeJsonUtil;
import com.rxt.trade.vo.ProductDetailVO;
import com.rxt.trade.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品服务实现。
 *
 * <p>本类里有两个贯穿全文的写法值得注意：
 *
 * <p><b>1. 列表接口一律用 {@code LambdaQueryWrapper} 显式过滤 {@code status}</b>，
 * 绝不用"查全部再在内存里筛"。因为商品列表对游客开放，
 * 漏掉过滤条件就等于把农户下架的商品、甚至已删除的商品暴露出去。
 *
 * <p><b>2. 涉及"是不是本人"的操作一律走 {@link #requireOwned}</b>，
 * 不依赖 {@code SecurityConfig} 的角色判断——角色只区分"是不是农户"，
 * 区分不了"是不是这件商品的农户"。
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final DictItemMapper dictItemMapper;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;

    /** 商品分类在字典表里的 type 值 */
    private static final String DICT_TYPE_PRODUCT_CATEGORY = "product_category";

    @Override
    public PageResult<ProductVO> list(String keyword, String categoryCode, String sort,
                                      Integer page, Integer size) {
        // 分页参数兜底：前端可能不传、传 0、传负数，或者传个 999999 想一次拉全表。
        // 上限必须卡住，否则等于给了一个免费的 DoS 入口。
        long current = (page == null || page < 1) ? DEFAULT_PAGE : page;
        long pageSize = (size == null || size < 1) ? DEFAULT_SIZE
                : Math.min(size, OrderConst.MAX_PAGE_SIZE);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                // 只查在售。游客也能调这个接口，漏掉这个条件会把下架/已删商品露出来
                .eq(Product::getStatus, ProductConst.STATUS_ON_SALE);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword.trim());
        }
        if (StringUtils.hasText(categoryCode)) {
            wrapper.eq(Product::getCategoryCode, categoryCode);
        }
        applySort(wrapper, sort);

        IPage<Product> result = productMapper.selectPage(new Page<>(current, pageSize), wrapper);

        List<ProductVO> vos = result.getRecords().stream()
                .map(ProductVO::from)
                .collect(Collectors.toList());
        fillFarmerNames(vos);

        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ProductDetailVO detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || isDeleted(product)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品不存在");
        }

        // 浏览量 +1。用 setSql 让数据库做原子自增，而不是"读出来 +1 再写回去"
        // ——后者在并发下会丢计数（两个请求都读到 10，都写回 11）。
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .setSql("view_count = view_count + 1"));

        ProductDetailVO vo = ProductDetailVO.from(product);
        // 上面查出来的还是自增前的值，手动同步一下返回给前端，
        // 否则用户刷新页面看到的浏览量总比实际少 1。
        vo.setViewCount(product.getViewCount() == null ? 1 : product.getViewCount() + 1);
        fillFarmerName(vo);
        return vo;
    }

    @Override
    public Long create(Long farmerId, ProductDTO dto) {
        validateCategory(dto.getCategoryCode());

        Product product = new Product();
        applyDto(dto, product);
        product.setFarmerId(farmerId);
        product.setViewCount(0);
        product.setSalesCount(0);
        // 新发布的商品默认上架
        product.setStatus(ProductConst.STATUS_ON_SALE);

        productMapper.insert(product);
        return product.getId();
    }

    @Override
    public void update(Long farmerId, Long id, ProductDTO dto) {
        Product product = requireOwned(farmerId, id);
        if (isDeleted(product)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        validateCategory(dto.getCategoryCode());

        applyDto(dto, product);
        productMapper.updateById(product);
    }

    @Override
    public void updateStatus(Long farmerId, Long id, Integer status) {
        Product product = requireOwned(farmerId, id);
        if (isDeleted(product)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        // status 只能是 0/1，Controller 的 DTO 已经用 @Min/@Max 卡过一遍，
        // 这里再挡一次是为了防"以后有人写内部调用绕过 Controller"。
        if (status == null || (status != ProductConst.STATUS_OFF_SHELF
                && status != ProductConst.STATUS_ON_SALE)) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态只能是 0（下架）或 1（上架）");
        }
        product.setStatus(status);
        productMapper.updateById(product);
    }

    @Override
    public void delete(Long farmerId, Long id) {
        Product product = requireOwned(farmerId, id);
        if (isDeleted(product)) {
            // 重复删除直接返回成功（幂等），不报错——
            // 用户连点两次删除按钮不该看到"操作失败"
            return;
        }
        // 软删除：订单明细里存着 productId，物理删会让历史订单失去商品来源
        product.setStatus(ProductConst.STATUS_DELETED);
        productMapper.updateById(product);
    }

    // ==================== 私有辅助 ====================

    /**
     * 查商品并校验归属。
     *
     * <p>这是挡越权的唯一关卡：{@code SecurityConfig} 里的 {@code hasRole("farmer")}
     * 只能保证"是农户"，保证不了"是这件商品的农户"。
     */
    private Product requireOwned(Long farmerId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        if (!farmerId.equals(product.getFarmerId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return product;
    }

    /**
     * 校验分类编码在字典里真实存在。
     *
     * <p>不校验的话，前端传一个乱写的 categoryCode 也能存进库，
     * 结果是这个商品在"按分类筛选"时永远查不出来（分类列表来自字典）。
     */
    private void validateCategory(String categoryCode) {
        Long count = dictItemMapper.selectCount(
                new LambdaQueryWrapper<DictItem>()
                        .eq(DictItem::getType, DICT_TYPE_PRODUCT_CATEGORY)
                        .eq(DictItem::getCode, categoryCode)
                        .eq(DictItem::getEnabled, 1));
        if (count == null || count == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品分类不存在：" + categoryCode);
        }
    }

    /** 把 DTO 的字段搬到实体上，同时处理 images / skuList 的 JSON 序列化 */
    private void applyDto(ProductDTO dto, Product product) {
        product.setName(dto.getName().trim());
        product.setCategoryCode(dto.getCategoryCode());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setUnit(dto.getUnit());
        product.setDescription(dto.getDescription());
        // List<String> → JSON 字符串存库
        product.setImages(TradeJsonUtil.toJson(dto.getImages()));
        product.setSkuList(dto.getSkuList() == null ? null : TradeJsonUtil.toJson(dto.getSkuList()));
    }

    /**
     * 批量补齐发布者姓名。
     *
     * <p>批量查而不是在循环里逐个查 {@code userMapper.selectById}——
     * 一页 12 个商品会变成 12 次额外的库查询（N+1 问题）。
     */
    private void fillFarmerNames(List<ProductVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> farmerIds = vos.stream()
                .map(ProductVO::getFarmerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (farmerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = userMapper.selectByIds(farmerIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getName() == null ? "" : u.getName(),
                        (a, b) -> a));
        vos.forEach(vo -> vo.setFarmerName(nameMap.get(vo.getFarmerId())));
    }

    private void fillFarmerName(ProductVO vo) {
        if (vo == null || vo.getFarmerId() == null) {
            return;
        }
        User farmer = userMapper.selectById(vo.getFarmerId());
        vo.setFarmerName(farmer == null || farmer.getName() == null ? "" : farmer.getName());
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        if (sort == null || sort.isBlank()) {
            // 综合排序：销量优先，同销量看谁新
            wrapper.orderByDesc(Product::getSalesCount).orderByDesc(Product::getCreateTime);
            return;
        }
        switch (sort) {
            case "price_asc" -> wrapper.orderByAsc(Product::getPrice);
            case "price_desc" -> wrapper.orderByDesc(Product::getPrice);
            case "sales" -> wrapper.orderByDesc(Product::getSalesCount);
            case "newest" -> wrapper.orderByDesc(Product::getCreateTime);
            // 未知排序值不报错，退回综合排序。前端拼错参数不该让页面白屏
            default -> wrapper.orderByDesc(Product::getSalesCount)
                    .orderByDesc(Product::getCreateTime);
        }
    }

    private boolean isDeleted(Product product) {
        return product.getStatus() != null && product.getStatus() == ProductConst.STATUS_DELETED;
    }
}
