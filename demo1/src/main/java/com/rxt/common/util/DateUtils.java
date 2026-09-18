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
}
