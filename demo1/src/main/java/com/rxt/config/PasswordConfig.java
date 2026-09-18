package com.rxt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p><b>⚠️ 这里有一个极易踩的坑：不要用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`。</b>
 * <br>它是 Spring Security 从 5.0 起推荐的"默认编码器"，但它会在哈希串前面加一个算法前缀
 * （例如 {@code {bcrypt}$2a$10$...}）。而我们 init.sql 里的种子密码存的是
 * <b>裸 BCrypt 串</b>（{@code $2a$10$DkHWvi...}），没有 {@code {bcrypt}} 前缀。
 * 用代理编码器去 matches，会直接抛 {@code IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"}。
 *
 * <p>所以这里显式 new 一个裸的 {@link BCryptPasswordEncoder}，与数据库里存的形式严格对应。
 *
 * <p><b>BCrypt 的两个特性（回答"为什么数据库里没有 salt 字段"）：</b>
 * <ol>
 *   <li><b>自带随机盐。</b>每次 encode 同一个密码，结果都不一样——盐被拼接在密文里，
 *       所以不需要单独建 salt 列。这也是为什么我们 DTO 里没有 salt 字段。</li>
 *   <li><b>不可逆。</b>验证只能靠 {@code matches(明文, 密文)}，无法"解密后比对"。</li>
 * </ol>
 *
 * <p><b>强度参数 cost=10</b>：10 表示做 2^10 = 1024 轮哈希，单次约 50-100ms。
 * 这是"防暴力破解"和"登录响应速度"的平衡点。种子数据里写死的 {@code $2a$10$} 就是这个 10，
 * 如果这里改成 cost=12，旧密码<b>依然能验证通过</b>（因为 cost 存在密文里），
 * 但新生成的密文 cost 会变成 12——两者可以共存，这是 BCrypt 的一个好设计。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
