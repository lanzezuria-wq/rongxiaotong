package com.rxt.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址 —— 对应 t_address。
 *
 * <p><b>业务规则：同一用户的默认地址只能有一条。</b>
 * 新增／编辑时如果 {@code isDefault = 1}，必须先把该用户其他地址的
 * {@code isDefault} 全部置 0，再写当前这条。这两步要在<b>同一个事务</b>里，
 * 否则中途失败会出现"两个默认地址"或"一个默认地址都没有"。
 *
 * <p>"唯一默认地址"这件事<b>数据库没法约束</b>——不能对 {@code is_default} 加唯一索引，
 * 因为一个用户会有多条 {@code is_default = 0} 的记录，唯一索引会直接挡掉。
 * 所以只能靠 Service 层保证，这是本项目里少见的"要用代码补数据库约束漏洞"的地方。
 */
@Data
@TableName("t_address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 收货人姓名 */
    private String receiver;

    /** 收货人手机号（11 位） */
    private String phone;

    private String province;
    private String city;
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认地址：0 否 / 1 是 */
    private Integer isDefault;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
