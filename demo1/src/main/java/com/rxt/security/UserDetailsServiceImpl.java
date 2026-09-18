package com.rxt.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.entity.User;
import com.rxt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户信息来源 —— 告诉 Spring Security"怎么按账号名把用户查出来"。
 *
 * <p>它是安全地基里<b>唯一依赖 Mapper 的类</b>，所以在写作顺序上必须排在
 * Entity + Mapper 之后。这也是为什么"先写业务的地基（entity/mapper），再写安全层"。
 *
 * <p>两个方法的分工：
 * <ul>
 *   <li>{@link #loadUserByUsername} —— 接口要求实现的方法，按手机号查。
 *       本项目登录不走 AuthenticationManager，所以它主要给"以后想接
 *       DaoAuthenticationProvider"留的口子；</li>
 *   <li>{@link #loadById} —— <b>本项目实际在用的</b>。JWT 过滤器解出 Token 里的
 *       userId 后，用这个把用户查出来重新装进 SecurityContext。</li>
 * </ul>
 *
 * <p><b>为什么每次请求都要查一次库？</b>因为 JWT 是自包含的，Token 里写着
 * {@code role=admin} 就一直是 admin。如果管理员把这个账号禁用或降权，
 * 老 Token 还能继续用满 24 小时——这在"管理员能禁用账号"（SRS 表38）的系统里是不能接受的。
 * 代价是每个请求多一次主键查询（走索引，微秒级）。
 * 想彻底去掉这次查询，就需要引入 Token 黑名单 / Redis 缓存，那超出了 P1 范围。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            // 注意：消息里不要写"手机号 xxx 不存在"，这个异常有可能被日志之外的途径看到。
            // 这里只用于框架内部，业务侧的登录提示统一由 UserServiceImpl 控制成"手机号或密码错误"。
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }

    /**
     * 按主键装载用户，供 JWT 过滤器使用。
     *
     * @return 用户不存在时返回 {@code null}（不回退成抛异常，
     *         因为"Token 里的用户已被删除"属于过滤器要自行处理的边界情况）
     */
    public LoginUser loadById(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : new LoginUser(user);
    }
}
