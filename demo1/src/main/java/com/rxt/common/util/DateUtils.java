package com.rxt.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期格式化工具。
 *
 * <p><b>为什么需要它？</b>因为项目做了一个刻意的决定：
 * <b>VO 里不暴露 {@code LocalDateTime}，只暴露格式化好的字符串。</b>
 *
 * <p>原因是 Jackson 的版本差异：Spring Boot 4 的 Web 层换成了 Jackson 3
 * （{@code tools.jackson} 包），而 Jackson 2 还在类路径上（jjwt 传递引入）。
 * {@code LocalDateTime} 默认会被序列化成 ISO-8601 的 {@code 2026-09-18T14:05:17}
 * （中间带一个 T），而需求文档要求的是 {@code 2026-09-18 14:05:17}。
 * 想统一格式，通常靠 {@code @JsonFormat} 注解或全局配置——但这两者
 * 都和 Jackson 主版本强耦合，换个版本行为就变。
 *
 * <p>把格式化提前到 VO 转换这一步，好处是：
 * <ol>
 *   <li>输出格式确定，不受任何序列化框架版本影响；</li>
 *   <li>前端不用再处理 ISO 字符串里的 {@code T} 和时区后缀；</li>
 *   <li>Entity 里依然是类型安全的 {@code LocalDateTime}，不影响数据库读写。</li>
 * </ol>
 *
 * <p>{@code DateTimeFormatter} 是<b>线程安全</b>的，所以可以放心用静态常量。
 * （对比 {@code SimpleDateFormat} 不是线程安全的，多线程下会解析出错误日期，是个经典坑。）
 */
public final class DateUtils {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private DateUtils() {
    }

    /** 格式化，null 安全 */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }

    /**
     * 把前端传来的时间字符串解析成 {@code LocalDateTime}，null / 空串返回 null。
     *
     * <p>P3 新增（M5 专家预约的 {@code appointmentTime} 需要）。
     *
     * <p><b>为什么要容忍两种格式？</b>因为不能假设前端用哪个组件：
     * <ul>
     *   <li>Element Plus 的 {@code el-date-picker} 配了
     *       {@code value-format="YYYY-MM-DD HH:mm:ss"} 时输出 {@code "2026-09-25 09:30:00"}；</li>
     *   <li>不配 {@code value-format} 时它输出 ISO 串 {@code "2026-09-25T09:30:00.000Z"}。</li>
     * </ul>
     * 只认第一种的话，第二种会在解析处炸掉，而报错信息（{@code DateTimeParseException}）
     * 对前端同学毫无帮助。这里统一兼容：先把 {@code T} 换成空格、丢掉毫秒和时区后缀，
     * 再按项目格式解析。
     *
     * <p>解析失败抛 {@link IllegalArgumentException}（而不是 DateTimeParseException），
     * 由调用方包成 {@code BizException(1001, "预约时间格式不正确…")}，
     * 这样用户看到的是可执行的提示而不是 500。
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace('T', ' ');
        // 丢掉毫秒（.000）
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        // 丢掉 Z 后缀
        int zone = normalized.indexOf('Z');
        if (zone > 0) {
            normalized = normalized.substring(0, zone);
        }
        // 丢掉 +08:00 / -05:00 这类时区偏移（前 19 位是 "yyyy-MM-dd HH:mm:ss"）
        if (normalized.length() > DATE_TIME_PATTERN.length()
                && (normalized.charAt(19) == '+' || normalized.charAt(19) == '-')) {
            normalized = normalized.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("时间格式不正确，应形如 2026-09-25 09:30:00");
        }
    }
}
