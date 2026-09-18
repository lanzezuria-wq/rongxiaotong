package com.rxt.vo;

import com.rxt.common.constant.DictConst;
import com.rxt.common.util.DateUtils;
import com.rxt.entity.DictItem;
import lombok.Data;

/**
 * 字典项出参。
 *
 * <p>相比实体多了两个"给前端减负"的字段：
 * <ul>
 *   <li>{@code enabled} 从数据库的 TINYINT（0/1）转成 JSON 布尔值——
 *       Element Plus 的 {@code el-switch} 组件绑定的就是 true/false，
 *       前端不用写 {@code :active-value="1"} 这种容易写错的配置；</li>
 *   <li>{@code typeName} 是类型的中文名（"产品分类"）。
 *       字典页的类型下拉框需要它，放在这里前端就不用自己维护一份
 *       "英文类型码 → 中文名"的映射表——那种映射一旦后端加类型，
 *       前端不同步就会显示成英文。</li>
 * </ul>
 */
@Data
public class DictVO {

    private Long id;
    /** 字典类型码，如 product_category */
    private String type;
    /** 类型中文名，如"产品分类" */
    private String typeName;
    private String code;
    private String name;
    private Integer sort;
    /** 是否启用。数据库存 0/1，对外暴露布尔值 */
    private Boolean enabled;
    private String remark;
    private String createTime;
    private String updateTime;

    public static DictVO from(DictItem item) {
        if (item == null) {
            return null;
        }
        DictVO vo = new DictVO();
        vo.setId(item.getId());
        vo.setType(item.getType());
        vo.setTypeName(DictConst.displayName(item.getType()));
        vo.setCode(item.getCode());
        vo.setName(item.getName());
        vo.setSort(item.getSort());
        vo.setEnabled(item.getEnabled() != null && item.getEnabled() == DictConst.ENABLED);
        vo.setRemark(item.getRemark());
        vo.setCreateTime(DateUtils.format(item.getCreateTime()));
        vo.setUpdateTime(DateUtils.format(item.getUpdateTime()));
        return vo;
    }
}
