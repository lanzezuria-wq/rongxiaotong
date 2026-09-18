package com.rxt.security;

import com.rxt.common.ResultCode;
import com.rxt.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户的读取工具。
 *
 * <p>给 Controller 用：{@code Long userId = SecurityUtils.currentUserId();}
 * 然后把它作为参数传给 Service。
 *
 * <p><b>为什么做成静态工具，而不是让 Service 直接读 SecurityContextHolder？</b>
 * 因为那样 Service 就绑死在 Web 环境上了——单元测试里没有请求上下文，
 * 一调用就 NPE。让 Controller 负责"从请求里取身份"，Service 只接收明确的
 * {@code userId} 参数，职责更干净，也更容易写出"管理员 A 修改用户 B"的测试。
 *
 * <p><b>这里抛异常而不是返回 null</b>：需要当前用户却没登录，属于程序逻辑问题，
 * 应该立刻失败。返回 null 会让错误延后到某一行 {@code userId.equals(...)} 才炸，
 * 那时候堆栈已经偏离现场了。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 取当前登录用户。
     *
     * <p>两层判断缺一不可：
     * <ol>
     *   <li>{@code principal instanceof LoginUser} —— 过滤掉匿名用户。
     *       匿名认证的 {@code isAuthenticated()} 也是 {@code true}，
     *       但它的 principal 是一个字符串 {@code "anonymousUser"}，不是 LoginUser；</li>
     *   <li>{@code auth == null} —— 请求根本没有经过认证（例如未放行的路径被直接调用）。</li>
     * </ol>
     */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ResultCode.LOGIN_FAILED, "未登录或登录状态无效，请先登录");
        }
        return loginUser;
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    /** 当前用户是否管理员 */
    public static boolean isAdmin() {
        return "admin".equals(currentUser().getRole());
    }
}
