package com.rxt.common.constant;

import java.util.List;
import java.util.Set;

/**
 * 数据字典常量（M7 表41 数据字典管理）。
 *
 * <p>SRS 表41 业务规则原文："字典类型固定（产品分类/资金用途/所属行业/贷款期限）"。
 * 这四个类型不是随便起的，它们<b>被其他模块的业务表引用</b>：
 * <ul>
 *   <li>{@code product_category} → P2 商品表的分类字段</li>
 *   <li>{@code fund_usage} → P3 融资申请表的资金用途</li>
 *   <li>{@code industry} → P3 融资申请表的所属行业</li>
 *   <li>{@code loan_term} → P3 融资申请表的贷款期限</li>
 * </ul>
 * 所以这里用白名单硬约束：新增字典项时类型必须落在这四个里，
 * 否则前端下拉框就查不到，等于往数据库里扔了一条死数据。
 *
 * <p>类型值用英文 snake_case 而不是中文——中文做键会在 URL 里被编码成
 * {@code %E4%BA%A7%E5%93%81%E5%88%86%E7%B1%BB}，既难看又容易在
 * 前后端拼接时出编码问题。中文只作为展示名，放在 {@link #displayName} 里。
 */
public final class DictConst {

    private DictConst() {
    }

    public static final String TYPE_PRODUCT_CATEGORY = "product_category";
    public static final String TYPE_FUND_USAGE = "fund_usage";
    public static final String TYPE_INDUSTRY = "industry";
    public static final String TYPE_LOAN_TERM = "loan_term";

    /** 合法的字典类型白名单 */
    public static final Set<String> ALL_TYPES = Set.of(
            TYPE_PRODUCT_CATEGORY, TYPE_FUND_USAGE, TYPE_INDUSTRY, TYPE_LOAN_TERM);

    /**
     * 供前端"数据字典"页渲染类型下拉框用。
     * 顺序有业务含义（按文档里出现的顺序），所以用 List 而不是 Set。
     */
    public static final List<String> ORDERED_TYPES = List.of(
            TYPE_PRODUCT_CATEGORY, TYPE_FUND_USAGE, TYPE_INDUSTRY, TYPE_LOAN_TERM);

    /** 类型 → 中文展示名 */
    public static String displayName(String type) {
        return switch (type) {
            case TYPE_PRODUCT_CATEGORY -> "产品分类";
            case TYPE_FUND_USAGE -> "资金用途";
            case TYPE_INDUSTRY -> "所属行业";
            case TYPE_LOAN_TERM -> "贷款期限";
            default -> type;
        };
    }

    /** 是否合法类型 */
    public static boolean isValid(String type) {
        return type != null && ALL_TYPES.contains(type);
    }

    /** 启用 / 停用（对应 t_dict.enabled 的 0/1） */
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
}
