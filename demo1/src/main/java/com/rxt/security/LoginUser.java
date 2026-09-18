package com.rxt.security;

import com.rxt.common.constant.UserConst;
import com.rxt.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体 —— 把数据库里的 {@link User} 适配成 Spring Security 认识的 {@code UserDetails}。
 *
 * <p><b>为什么不直接把 User 实体当 principal？</b>
 * 因为 Spring Security 的过滤器链、{@code @PreAuthorize}、{@code Authentication}
 * 全都是按 {@code UserDetails} 接口编程的。给它一个实体，它不认识。
 * 这个类就是那座桥——同时它还承担了"只把鉴权需要的字段暴露出去"的职责
 * （比如不需要把邮箱、地址塞进来）。
 *
 * <p><b>关于 authorities 的命名约定（最容易错的一处）：</b>
 * Spring Security 的 {@code hasRole("admin")} 会自动给参数补上前缀 {@code ROLE_}，
 * 也就是实际比对的是 {@code ROLE_admin}；而 {@code hasAuthority("xxx")} 则是原样比对。
 * 所以这里必须把角色拼成 {@code ROLE_ + role}，
 * 否则 {@code hasRole("admin")} 永远不成立，表现是"管理员也被 403 拦住"。
 *
 * <p><b>关于那四个 {@code isXxx} 方法</b>：它们本来是被
 * {@code AuthenticationManager} 在认证时调用的。本项目登录逻辑写在 Service 里
 * （手动 {@code passwordEncoder.matches}），不走 AuthenticationManager，
 * 所以这几个方法的返回值不参与登录判定——但它们仍是"账号状态"的权威表达，
 * 留着并写对，是防止以后有人接 AuthenticationManager 时踩坑。
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String phone;
    /** farmer / expert / bank / admin */
    private final String role;
    /** BCrypt 密文。UserDetails 接口要求提供，不会被打进任何响应体 */
    private final String password;
    /** 0 待审核 / 1 正常 / 2 已拒绝 / 3 已禁用 */
    private final Integer status;

    public LoginUser(User user) {
        this.userId = user.getId();
        this.phone = user.getPhone();
        this.role = user.getRole();
        this.password = user.getPassword();
        this.status = user.getStatus();
    }

    /** 角色 → 权限。前缀 ROLE_ 是 Spring Security 的硬约定，不能省 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** UserDetails 里的 username 对应我们的手机号（登录账号） */
    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == null || status != UserConst.STATUS_DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 只有 status=1 正常 的账号才算启用 */
    @Override
    public boolean isEnabled() {
        return status != null && status == UserConst.STATUS_NORMAL;
    }
}
