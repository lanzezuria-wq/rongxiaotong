package com.rxt.security;

import com.rxt.common.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录/注册接口的 IP 限流器 —— 触发时抛 1005（HTTP 429）。
 *
 * <p><b>它和 UserServiceImpl 里的"连续输错 5 次锁定 30 分钟"是两件事，别混：</b>
 * <table border="1">
 *   <tr><th>维度</th><th>账号级锁定（Service 里）</th><th>IP 限流（本类）</th></tr>
 *   <tr><td>计数对象</td><td>某个手机号</td><td>某个来源 IP</td></tr>
 *   <tr><td>触发条件</td><td>5 次密码错误</td><td>时间窗内请求次数过多</td></tr>
 *   <tr><td>防的是</td><td>针对单个账号的密码爆破</td><td>用脚本批量扫不同手机号（撞库）</td></tr>
 * </table>
 * 只有账号级锁定的话，攻击者换着手机号试就绕过去了。
 *
 * <p><b>算法：滑动窗口。</b>每个 IP 维护一个"请求时间戳队列"，
 * 每次请求先把窗口外的旧时间戳丢掉，再看队列长度是否超限。
 * 比"固定窗口"（每分钟清零一次）精确——固定窗口在窗口交界处
 * 允许 2 倍流量通过（59 秒打 20 次 + 61 秒打 20 次）。
 *
 * <p><b>⚠️ 这个实现是单机内存版，有明确的适用边界：</b>
 * <ul>
 *   <li>应用多实例部署时，每个实例各算各的，实际阈值会放大 N 倍。
 *       要精确需要换成 Redis + Lua 或网关层限流（P1 不涉及）。</li>
 *   <li>重启即清空——对"防爆破"这个目标是可接受的（重启不常见，
 *       而且账号级锁定是存在数据库里的，重启也不丢）。</li>
 * </ul>
 * 之所以先用内存版，是因为它零依赖、行为可预测，够 P1 的验收用。
 */
@Slf4j
@Component
public class LoginRateLimiter {

    /** 时间窗内允许的最大请求数 */
    @Value("${rxt.security.rate-limit.max-requests:20}")
    private int maxRequests;

    /** 时间窗长度（秒） */
    @Value("${rxt.security.rate-limit.window-seconds:300}")
    private long windowSeconds;

    /** key(IP) → 该 IP 在窗口内的请求时间戳队列 */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * 记录一次请求并检查是否超限。超限直接抛 {@link TooManyRequestsException}。
     *
     * <p>注意顺序：<b>先检查再入队</b>。反过来的话，第 21 次请求会先被记进去再被拒绝，
     * 导致攻击者每被拒一次都在把窗口往后推，永远解不开。
     */
    public void check(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        Deque<Long> queue = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (queue) {
            // 丢弃滑出窗口的旧记录
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }
            if (queue.size() >= maxRequests) {
                log.warn("IP {} 在 {} 秒内请求 {} 次，触发限流", ip, windowSeconds, queue.size());
                throw new TooManyRequestsException("操作过于频繁，请稍后再试");
            }
            queue.addLast(now);
        }

        // 顺带清理长期不活跃的 key，避免 Map 随 IP 数量无限增长（内存泄漏）
        if (hits.size() > 10_000) {
            evictIdle(windowStart);
        }
    }

    /** 清掉窗口内已经没有任何记录的 IP */
    private void evictIdle(long windowStart) {
        Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> queue = it.next().getValue();
            synchronized (queue) {
                if (queue.isEmpty() || queue.peekLast() < windowStart) {
                    it.remove();
                }
            }
        }
        log.debug("限流器清理完成，剩余监控 IP 数：{}", hits.size());
    }
}
