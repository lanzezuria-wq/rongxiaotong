package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.DictConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.dto.DictDTO;
import com.rxt.entity.DictItem;
import com.rxt.mapper.DictItemMapper;
import com.rxt.service.DictService;
import com.rxt.service.OperationLogService;
import com.rxt.security.SecurityUtils;
import com.rxt.trade.mapper.ProductMapper;
import com.rxt.trade.entity.Product;
import com.rxt.finance.mapper.FinancingApplyMapper;
import com.rxt.finance.entity.FinancingApply;
import com.rxt.vo.DictVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典服务实现 —— M7 表41。
 *
 * <p><b>这个类里最关键的一行是查重条件的写法。</b>
 * 因为数据库的唯一索引是 {@code uk_dict_type_code (type, code)}——
 * 复合索引，所以"编码唯一"是<b>在同一个类型内</b>唯一，跨类型可以重名。
 * 查重时必须写成 {@code WHERE type=? AND code=?}；
 * 只写 {@code WHERE code=?} 会把别的类型下的同名编码误报成冲突——
 * 例如 {@code industry} 里已经有 {@code PROCESSING}，
 * 再往 {@code product_category} 加 {@code PROCESSING} 就会被错误拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictItemMapper dictItemMapper;
    private final OperationLogService operationLogs;
    private final ProductMapper productMapper;
    private final FinancingApplyMapper financingMapper;

    @Override
    public List<DictVO> listByType(String type) {
        assertTypeValid(type);
        List<DictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getType, type)
                .orderByAsc(DictItem::getSort)
                // 加 id 兜底：sort 相同时保证顺序稳定。
                // 否则同一批数据两次查询可能返回不同顺序，前端看起来像"列表在乱跳"
                .orderByAsc(DictItem::getId));
        return items.stream().map(DictVO::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictVO create(DictDTO dto) {
        assertTypeValid(dto.getType());
        String code = dto.getCode().trim();
        assertCodeUnique(dto.getType(), code, null);

        DictItem item = new DictItem();
        item.setType(dto.getType());
        item.setCode(code);
        item.setName(dto.getName().trim());
        item.setSort(dto.getSort() == null ? 0 : dto.getSort());
        // enabled 不传时默认启用。Boolean.FALSE.equals 的写法是 null 安全的：
        // 如果写成 !dto.getEnabled()，不传 enabled 就会 NPE
        item.setEnabled(Boolean.FALSE.equals(dto.getEnabled()) ? DictConst.DISABLED : DictConst.ENABLED);
        item.setRemark(dto.getRemark());

        dictItemMapper.insert(item);
        operationLogs.record(SecurityUtils.currentUserIdOrNull(), "新增字典项", item.getId());
        log.info("新增字典项：type={}, code={}, name={}", item.getType(), item.getCode(), item.getName());

        // 重新查一次再返回，这样 createTime / updateTime 是数据库真实写入的值
        return DictVO.from(dictItemMapper.selectById(item.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictVO update(Long id, DictDTO dto) {
        DictItem exist = dictItemMapper.selectById(id);
        if (exist == null) {
            throw new ResourceNotFoundException("字典项不存在");
        }
        assertTypeValid(dto.getType());
        String code = dto.getCode().trim();
        // 排除自己：否则"只改名称、编码不变"也会被判定成编码冲突
        if ((!exist.getType().equals(dto.getType()) || !exist.getCode().equals(code)) && isReferenced(exist)) {
            throw new BizException(ResultCode.PARAM_ERROR, "该字典项正在被使用，不能修改类型或编码");
        }
        assertCodeUnique(dto.getType(), code, id);

        DictItem update = new DictItem();
        update.setId(id);
        update.setType(dto.getType());
        update.setCode(code);
        update.setName(dto.getName().trim());
        // 排序号 / 启用状态不传时保持原值：这是"部分更新"语义，
        // 因为前端编辑弹窗里如果漏传某个字段，用户不该发现这个字段被清空了
        update.setSort(dto.getSort() == null ? exist.getSort() : dto.getSort());
        update.setEnabled(dto.getEnabled() == null
                ? exist.getEnabled()
                : (dto.getEnabled() ? DictConst.ENABLED : DictConst.DISABLED));
        update.setRemark(dto.getRemark());

        dictItemMapper.updateById(update);
        operationLogs.record(SecurityUtils.currentUserIdOrNull(), "修改字典项", id);
        log.info("编辑字典项：id={}, type={}, code={}", id, update.getType(), update.getCode());

        return DictVO.from(dictItemMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictItem exist = dictItemMapper.selectById(id);
        if (exist == null) {
            throw new ResourceNotFoundException("字典项不存在");
        }
        if (isReferenced(exist)) {
            // 表41 异常处理原文："删除被引用的字典项 → 提示该字典项正在被使用，无法删除"
            throw new BizException(ResultCode.PARAM_ERROR, "该字典项正在被使用，无法删除");
        }
        // 物理删除：t_dict 没有 deleted 列，字典项没有"回收站"语义
        dictItemMapper.deleteById(id);
        operationLogs.record(SecurityUtils.currentUserIdOrNull(), "删除字典项", id);
        log.info("删除字典项：id={}, type={}, code={}", id, exist.getType(), exist.getCode());
    }

    /** 已被业务数据引用的编码必须保持可解析。 */
    private boolean isReferenced(DictItem item) {
        if (DictConst.TYPE_PRODUCT_CATEGORY.equals(item.getType())) {
            return productMapper.selectCount(new LambdaQueryWrapper<Product>()
                    .eq(Product::getCategoryCode, item.getCode())) > 0;
        }
        LambdaQueryWrapper<FinancingApply> query = new LambdaQueryWrapper<>();
        switch (item.getType()) {
            case DictConst.TYPE_FUND_USAGE -> query.eq(FinancingApply::getUsage, item.getCode());
            case DictConst.TYPE_INDUSTRY -> query.eq(FinancingApply::getIndustry, item.getCode());
            case DictConst.TYPE_LOAN_TERM -> query.eq(FinancingApply::getLoanTerm, item.getCode());
            default -> throw new BizException(ResultCode.PARAM_ERROR, "未知字典类型");
        }
        return financingMapper.selectCount(query) > 0;
    }

    /** 类型白名单校验。表41："字典类型固定（产品分类/资金用途/所属行业/贷款期限）" */
    private void assertTypeValid(String type) {
        if (!DictConst.isValid(type)) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "不支持的字典类型：" + type + "，合法取值：" + DictConst.ORDERED_TYPES);
        }
    }

    /**
     * 同类型内编码唯一性校验。
     *
     * @param excludeId 编辑时要排除自己的 id；新增时传 null
     */
    private void assertCodeUnique(String type, String code, Long excludeId) {
        Long count = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getType, type)
                .eq(DictItem::getCode, code)
                .ne(excludeId != null, DictItem::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该类型下编码已存在：" + code);
        }
    }
}
