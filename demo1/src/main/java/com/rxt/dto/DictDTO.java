package com.rxt.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典项入参 —— 新增（{}POST /api/admin/dict}）与编辑（{@code PUT /api/admin/dict/{id}}）共用。
 *
 * <p>SRS 表41 业务规则："新增字典项需填写：编码、名称、排序、是否启用"。
 * 请求体的四个字段名取自概要设计 5.2.7：{@code type, code, name, sort, enabled}。
 *
 * <p><b>三个字段的校验思路不同：</b>
 * <ul>
 *   <li>{@code type} —— 只校验非空。<b>取值白名单在 Service 校验</b>，
 *       因为白名单是从 {@code DictConst} 来的业务常量，将来加类型不该改 DTO；</li>
 *   <li>{@code code} / {@code name} —— 长度按数据库列宽（都是 VARCHAR(50)）限制。
 *       在 DTO 就拦住，比让数据库抛 "Data too long for column" 友好得多，
 *       后者会变成 1000 系统错误；</li>
 *   <li>{@code enabled} —— 用 Boolean。前端 Element Plus 的开关组件直接给 true/false，
 *       不用前端做 1/0 转换。落到数据库时再转成 TINYINT。</li>
 * </ul>
 */
@Data
public class DictDTO {

    /** 字典类型，白名单：product_category / fund_usage / industry / loan_term */
    @NotBlank(message = "字典类型不能为空")
    @Size(max = 50, message = "字典类型过长")
    private String type;

    /** 编码，同类型内唯一 */
    @NotBlank(message = "编码不能为空")
    @Size(max = 50, message = "编码不能超过 50 个字符")
    private String code;

    /** 显示名 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 个字符")
    private String name;

    /** 排序号，升序。不传默认 0 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sort;

    /** 是否启用。不传默认启用 */
    private Boolean enabled;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
