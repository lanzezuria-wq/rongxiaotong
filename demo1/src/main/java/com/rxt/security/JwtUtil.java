package com.rxt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 生成与解析 Token。
 *
 * <p><b>零依赖，所以它是安全地基里第一个能写的类。</b>
 * 它不依赖 Mapper、不依赖数据库、不依赖 Spring Security，只依赖 jjwt 库。
 *
 * <p><b>⚠️ jjwt 0.12.x 的 API 和网上教程（0.9 / 0.11）完全不同，照抄必编译不过：</b>
 * <table border="1">
 *   <tr><th>老写法（0.9/0.11）</th><th>新写法（0.12.x）</th></tr>
 *   <tr><td>{@code Jwts.parserBuilder().setSigningKey(key)}</td><td>{@code Jwts.parser().verifyWith(key)}</td></tr>
 *   <tr><td>{@code .setSubject() / .setExpiration()}</td><td>{@code .subject() / .expiration()}</td></tr>
 *   <tr><td>{@code .signWith(SignatureAlgorithm.HS256, str)}</td><td>{@code .signWith(SecretKey, Jwts.SIG.HS256)}</td></tr>
 *   <tr><td>{@code parseClaimsJws(token).getBody()}</td><td>{@code parseSignedClaims(token).getPayload()}</td></tr>
 * </table>
 *
 * <p><b>⚠️ 实测踩到的坑：{@code signWith(key)} 单参数版会"按密钥长度自动挑最强算法"。</b>
 * <br>不是你以为的 HS256——它会依次尝试：
 * <pre>
 *   密钥 >= 512 bit → HS512
 *   密钥 >= 384 bit → HS384
 *   密钥 >= 256 bit → HS256
 * </pre>
 * 本项目的 secret 是 59 字节 = 472 bit，所以<b>实际签出来的是 HS384</b>
 * （看 Token 头部 {@code eyJhbGciOiJIUzM4NCJ9} 里的 {@code HS384} 就能验证）。
 * 问题在于：哪天有人把 secret 改长到 64 字节，算法会悄悄变成 HS512，
 * 而文档里写的是 HS256——这种"配置一改行为就变"的隐式依赖必须消除。
 * 所以下面用<b>双参数版显式指定 HS256</b>，让行为可预期。
 *
 * <p><b>HS256 的密钥长度硬约束：</b>至少 256 bit = 32 字节。
 * 短于 32 字节时 {@link Keys#hmacShaKeyFor} 会直接抛 {@code WeakKeyException}，
 * <b>应用启动直接失败</b>（因为这个 Bean 构造器在启动时执行）。所以不要随手把 secret 改短。
 */
@Component
public class JwtUtil {

    /** 签名密钥。HS256 是对称加密，同一个 key 既用于签发也用于验签 */
    private final SecretKey key;

    /** Token 有效期（秒）。需求规定 24 小时 */
    private final long expireSeconds;

    public JwtUtil(@Value("${rxt.jwt.secret}") String secret,
                   @Value("${rxt.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireHours * 3600;
    }

    /**
     * 生成 Token。
     *
     * <p>三个信息写进 payload：
     * <ul>
     *   <li>{@code sub}（subject）—— 用户 id。用 sub 而不是自定义 claim，因为这是 JWT 标准字段，
     *       解析时一行 {@code getSubject()} 就能拿到；</li>
     *   <li>{@code phone}、{@code role} —— 放进 Token 后，鉴权过滤器不用查库就能知道角色，
     *       省一次数据库往返。代价是改了角色要等 Token 过期才生效（P1 可以接受）。</li>
     * </ul>
     *
     * <p>注意：Token 里<b>绝不放密码或任何敏感信息</b>。JWT 的 payload 只是 Base64 编码，
     * 不是加密——任何人复制这个串去 base64 解码都能看到内容。
     */
    public String generate(Long userId, String phone, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", phone)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验签。
     *
     * <p>签名不对、格式不对、已过期，三种情况都会抛 {@code JwtException}。
     * 这里不 try-catch，交给调用方（鉴权过滤器）决定怎么处理——
     * 过滤器把过期翻译成 1006、其他翻译成 1002。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Token 里取用户 id。sub 存的是字符串，这里转回 Long */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /** 供登录接口返回给前端：Token 还有多少秒过期 */
    public long getExpireSeconds() {
        return expireSeconds;
    }
}
