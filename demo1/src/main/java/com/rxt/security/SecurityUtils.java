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

    // ==================================================================================
    // 对"游客也能访问"的接口提供的不抛异常版本（P3 新增）
    // ==================================================================================

    /**
     * 取当前登录用户 id，<b>未登录时返回 null 而不是抛异常</b>。
     *
     * <p><b>为什么必须有这一对方法？</b>因为项目里有几个接口是
     * {@code permitAll}、但对已登录用户会返回更多内容的（P3 新增了
     * {@code GET /api/article/{id}} 和 {@code GET /api/expert/{id}}）：
     * <ul>
     *   <li>文章详情里 {@code isLiked} 要按当前用户算，游客恒为 0；</li>
     *   <li>未发布的文章，作者本人能看到、游客看不到 —— 要判断"你是不是作者"。</li>
     * </ul>
     * 这类接口如果直接调 {@link #currentUserId()}，游客一访问就会抛
     * "未登录或登录状态无效" —— 明明这个接口是对游客开放的，却报登录错误，
     * 表现非常费解。所以专门提供"可能为 null"的版本，由调用方显式处理游客分支。
     *
     * <p><b>取名用 {@code OrNull} 而不是重载 {@code currentUserId()}：</b>
     * 重载会让"到底调的是哪个"难以一眼看出（两个方法只差返回类型），
     * 而这两个方法的行为差异（抛异常 vs 返回 null）恰恰是调用方必须意识到的。
     * 显式的名字能让 review 的人立刻看到"这里考虑了未登录的情况"。
     */
    public static Long currentUserIdOrNull() {
        LoginUser loginUser = currentUserOrNull();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /** 取当前登录用户角色，未登录时返回 null。用途见 {@link #currentUserIdOrNull()} */
    public static String currentRoleOrNull() {
        LoginUser loginUser = currentUserOrNull();
        return loginUser == null ? null : loginUser.getRole();
    }

    /**
     * 取当前登录用户，未登录/匿名时返回 null。
     *
     * <p>判断逻辑和 {@link #currentUser()} 完全一样，差别只在"没登录"时
     * 一个抛异常、一个返回 null。
     */
    public static LoginUser currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }
}
