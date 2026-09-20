package com.rxt.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 融资申请 —— 对应 t_financing_apply（M4）。
 *
 * <p>状态流转见 {@link com.rxt.finance.constant.FinancingConst}：
 * 0 待匹配 → 1 已匹配 → 2 待银行审批 → 3 已通过 / 4 已拒绝，
 * 任意进行中状态都可以被申请人取消成 5。
 *
 * <p><b>两个字段要特别小心：</b>
 * <ul>
 *   <li>{@code usage} —— MySQL 关键字，必须用 {@code @TableField} 加反引号（见字段注释）；</li>
 *   <li>{@code groupId} —— 对应 t_guarantee_group.id 这个业务字段，
 *       不是 MyBatis-Plus 的分组概念，只是恰好同名。</li>
 * </ul>
 *
 * <p>{@code deleted} 字段存在，所以 MyBatis-Plus 的全局逻辑删除会自动生效
 * （所有查询自动带上 {@code AND deleted = 0}）。这与 P1 的 t_user / t_article 一致。
 */
@Data
@TableName("t_financing_apply")
public class FinancingApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人 t_user.id */
    private Long userId;

    /** 申请金额（元）。用 BigDecimal 而不是 double —— 金额绝不能用浮点数 */
    private BigDecimal amount;

    /**
     * 资金用途，字典 fund_usage 的编码。
     *
     * <p><b>⚠️ 这一列必须加反引号，不能省。</b>
     * {@code usage} 是 MySQL 的关键字（{@code GRANT ... USAGE} 里用到），
     * 在 {@code INSERT INTO t (...)} 的<b>列名位置</b>会被当成关键字解析，导致
     * {@code SQLSyntaxErrorException: You have an error in your SQL syntax ... near 'usage, industry, ...'}。
     *
     * <p>这是实测踩到的坑（MySQL 8.0.12 复现），不是理论风险：
     * 建表脚本里写了反引号所以 DDL 能过，但 MyBatis-Plus 自动生成的
     * INSERT / UPDATE / SELECT 是<b>不加反引号</b>的，于是运行期才炸 —— 编译和启动都发现不了。
     * {@code @TableField} 的值里带上反引号，MP 就会在所有语句里都保留它。
     *
     * <p>注意 Java 属性名保持 {@code usage}（契约里也叫 usage），只有数据库列需要转义。
     */
    @TableField("`usage`")
    private String usage;

    /** 所属行业，字典 industry 的编码 */
    private String industry;

    private String province;
    private String city;

    /** 用途说明 / 还款来源说明 */
    private String description;

    /** 贷款期限，字典 loan_term 的编码，如 M12 */
    private String loanTerm;

    /** 0 待匹配 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝 / 5 已取消 */
    private Integer status;

    /** 所属联保小组，待匹配时为 null */
    private Long groupId;

    /** 审批银行 t_user.id，审批后回填 */
    private Long bankId;

    /** 逻辑删除：0 未删 / 1 已删 */
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
