package com.rxt.finance.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 开关与 Mock 数据生成器 —— 银行审批用。
 *
 * <p><b>为什么金融/审批类接口一定要有 Mock 开关？</b>
 * 因为真实场景里"银行审批"要连征信、连核心系统，演示环境根本没有这些外部依赖。
 * 如果代码里硬编码"等外部回调"，演示时流程永远走不完。
 * 所以契约里明确写了"所有金融/支付/审批均可一键 Mock，开关在 MockConfig.MOCK_MODE"。
 *
 * <p><b>开关打开时的行为</b>：审批接口允许只传 {@code groupId + result}，
 * 信用分、利率、核定额度由这里生成。
 * <b>关闭时</b>：这三项必须由前端传全，否则报参数错误
 * —— 故意不"自动生成"，因为真实审批里让系统自己编利率是不可接受的。
 * 这个差异由 {@code BankServiceImpl} 判断 {@link #isEnabled()} 实现。
 *
 * <p><b>为什么放在 {@code com.rxt.finance.config} 而不是 P1 的 {@code com.rxt.config}？</b>
 * 跟 P2 建立的"模块子包隔离"约定一致：只有银行审批用得到它，
 * 放在自己的包里边界的清晰；将来若 M3 也要 Mock 支付，再提升到公共包也不迟。
 *
 * <p>用 {@code @Value} 而不是 {@code @ConfigurationProperties}：
 * 只需要读 2 个值，为它建一个 properties 类反而更绕。
 * P1 的 {@code UserServiceImpl} 也是这个写法（读 {@code rxt.mock.verify-code}），保持一致。
 */
@Slf4j
@Getter
@Component
public class MockConfig {

    /**
     * 是否启用 Mock 模式。默认 true —— 默认值选"开"是因为
     * 不开的话所有审批相关接口都无法在没接外部系统时跑通，等于新人拉下代码就跑不起来。
     * 上线前在 application-prod.yml 里关掉。
     */
    @Value("${rxt.mock.enabled:true}")
    private boolean enabled;

    /** Mock 验证码固定值（M1 注册用；P1 的 UserServiceImpl 也读这个 key，这里一并暴露方便排查） */
    @Value("${rxt.mock.verify-code:111111}")
    private String verifyCode;

    // ==================================================================================
    // 审批三项的 Mock 生成规则
    // ==================================================================================

    /** 信用评分下限。60 分是常见的"可接受"门槛——低于它审批员会倾向拒绝 */
    private static final int CREDIT_SCORE_MIN = 60;
    /** 信用评分上限 */
    private static final int CREDIT_SCORE_MAX = 100;

    /** 利率下限，与契约约定一致（3.85 附近是 LPR 基准） */
    private static final BigDecimal RATE_MIN = new BigDecimal("3.85");
    /** 利率上限，与契约约定一致 */
    private static final BigDecimal RATE_MAX = new BigDecimal("7.00");

    /** 核定额度相对申请总额的比例下限（80%） */
    private static final BigDecimal RATIO_MIN = new BigDecimal("0.80");
    /** 比例上限（100%）—— 核定额度超过申请额没有业务含义 */
    private static final BigDecimal RATIO_MAX = new BigDecimal("1.00");

    /**
     * Mock 信用评分：60~100 的整数。
     *
     * <p>用 {@code ThreadLocalRandom} 而不是 {@code Math.random()}：
     * 后者内部有共享的 {@code Random} 实例（带同步），高并发下会成为瓶颈。
     */
    public int randomCreditScore() {
        return ThreadLocalRandom.current().nextInt(CREDIT_SCORE_MIN, CREDIT_SCORE_MAX + 1);
    }

    /**
     * Mock 利率：3.85 ~ 7.00，保留 2 位小数。
     *
     * <p>先取一个 {@code [385, 700]} 的整数"分"，再 {@code /100}，
     * 而不是 {@code nextDouble() * 3.15 + 3.85}。
     * 后者会得到 {@code 5.4783219…} 这种一串小数，
     * 而利率在业务上是精确到"分"的（数据库列也是 {@code DECIMAL(5,2)}）。
     * 用整数分再除，从生成那一刻就没有精度问题。
     */
    public BigDecimal randomInterestRate() {
        int basisPoints = ThreadLocalRandom.current().nextInt(
                RATE_MIN.movePointRight(2).intValueExact(),
                RATE_MAX.movePointRight(2).intValueExact() + 1);
        return new BigDecimal(basisPoints).movePointLeft(2);
    }

    /**
     * Mock 核定额度：申请总额的 80%~100%，保留 2 位小数。
     *
     * <p>为什么统一按一个比例打折，而不是每个成员各自打折？
     * 因为联保贷款的额度是<b>按小组</b>核定的，组内成员共享一个结论。
     * 要按人核定那是另一种产品（个人信用贷），不属于本模块。
     *
     * <p>{@code roundingMode = HALF_UP} 必须显式指定：
     * 不指定时 {@code setScale(2)} 会抛 {@code ArithmeticException}
     * （因为无法无损取整）。这是 BigDecimal 最常见的运行期异常之一。
     */
    public BigDecimal decideAmount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // 比例也用"整数百分比"生成，理由同利率：避免生成出无限小数
        int percent = ThreadLocalRandom.current().nextInt(
                RATIO_MIN.movePointRight(2).intValueExact(),
                RATIO_MAX.movePointRight(2).intValueExact() + 1);
        return totalAmount
                .multiply(new BigDecimal(percent))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
