package com.rxt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据字典项 —— 对应 t_dict。
 *
 * <p>类名叫 {@code DictItem} 而不是 {@code Dict}，是为了避免和
 * {@code java.util.Dict}（不存在）、以及各种框架里的 Dict 类重名。
 * 更重要的是：<b>它表示的是"一个字典项"，不是"一张字典"</b>——
 * 产品分类下的一条"蔬菜"才是这个类的一个实例。
 *
 * <p>{@code @TableName("t_dict")} 不能省：类名是 DictItem，表名是 t_dict，
 * 不一致时必须显式声明，否则 MyBatis-Plus 会去找 {@code t_dict_item} 报
 * {@code Table 'rxt_db.t_dict_item' doesn't exist}。
 *
 * <p><b>{@code (type, code)} 上有唯一索引</b>（见 init.sql 的 {@code uk_dict_type_code}）。
 * 这意味着"同一个类型下编码不能重复"，但不同类型下可以有同名编码——
 * 比如 {@code industry} 和 {@code product_category} 里都能有 {@code PROCESSING}。
 * 所以新增/修改时的查重条件必须<b>同时</b>带上 type 和 code，只查 code 会误判。
 *
 * <p>同样地，这个实体没有 {@code deleted} 属性，所以不会被加上逻辑删除条件——
 * 字典项删除是物理删除（表41 的删除语义就是删掉，没有"回收站"要求）。
 */
@Data
@TableName("t_dict")
public class DictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型：product_category / fund_usage / industry / loan_term */
    private String type;

    /** 编码，同类型内唯一 */
    private String code;

    /** 显示名 */
    private String name;

    /** 排序号，升序 */
    private Integer sort;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
