package com.rxt.controller;

import com.rxt.common.Result;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.dto.UpdateInfoDTO;
import com.rxt.dto.UpdatePasswordDTO;
import com.rxt.security.LoginRateLimiter;
import com.rxt.security.SecurityUtils;
import com.rxt.service.UserService;
import com.rxt.vo.LoginVO;
import com.rxt.vo.RegisterVO;
import com.rxt.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户体系接口 —— M1 模块，共 5 个接口（SRS 表53 / 概要设计 5.2.3）。
 *
 * <p><b>这个类应该很薄，薄到几乎没有逻辑。</b>它只做三件事：
 * <ol>
 *   <li><b>校验入参</b> —— {@code @Valid} 触发 DTO 上的注解，失败自动抛
 *       {@code MethodArgumentNotValidException}，被全局异常处理器翻译成 1001；</li>
 *   <li><b>取出当前用户 id</b> —— 通过 {@link SecurityUtils}，然后显式传给 Service；</li>
 *   <li><b>把返回值包成 {@link Result}</b> —— 成功文案写在这里，业务规则全在 Service。</li>
 * </ol>
 * 如果发现自己在 Controller 里写了 {@code if} 判断业务规则，那说明代码放错层了。
 *
 * <p><b>为什么每个方法都返回 {@code Result<XxxVO>} 而不是直接返回 VO？</b>
 * 为了统一响应结构。前端只需要在 axios 响应拦截器里写一次
 * {@code if (res.data.code !== 0) throw ...}，就能覆盖全部接口。
 */
@Tag(name = "01. M1 用户体系", description = "注册、登录、个人信息、修改密码（5 个接口）")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;

    @Operation(summary = "用户注册",
            description = "角色只允许 farmer（农户/买家）或 expert（专家）。"
                    + "验证码 Mock 固定为 111111。"
                    + "农户注册后 status=1 并直接返回 Token（自动登录）；"
                    + "专家注册后 status=0，token 为 null，需等待管理员审核。")
    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto,
                                       HttpServletRequest request) {
        // IP 级限流放在最先：防止用脚本批量注册/撞库。
        // 账号级"连错 5 次锁定"在 Service 里，两者互补，缺一不可。
        loginRateLimiter.check(clientIp(request));
        RegisterVO vo = userService.register(dto);
        return Result.ok(vo.getNeedAudit() ? "注册成功，请等待管理员审核" : "注册成功", vo);
    }

    @Operation(summary = "用户登录",
            description = "成功返回 JWT，有效期 24 小时。连续输错 5 次锁定账号 30 分钟。")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto,
                                 HttpServletRequest request) {
        loginRateLimiter.check(clientIp(request));
        return Result.ok("登录成功", userService.login(dto));
    }

    @Operation(summary = "获取当前用户信息", description = "需要携带 Token")
    @GetMapping("/info")
    public Result<UserVO> info() {
        return Result.ok(userService.getInfo(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "更新个人信息",
            description = "部分更新，只传要改的字段。手机号不可修改（DTO 里没有该字段）。")
    @PutMapping("/info")
    public Result<UserVO> updateInfo(@Valid @RequestBody UpdateInfoDTO dto) {
        return Result.ok("保存成功", userService.updateInfo(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "修改密码",
            description = "需要提供原密码。新密码不能与原密码相同，长度 6-20 位。")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody UpdatePasswordDTO dto) {
        userService.changePassword(SecurityUtils.currentUserId(), dto);
        // 提示前端：改完密码要清掉本地 Token 并跳回登录页
        // （JWT 无状态，服务端无法主动作废，这一点已在 UpdatePasswordDTO 的注释里说明）
        return Result.ok("密码修改成功，请重新登录", null);
    }

    /**
     * 取客户端真实 IP。
     *
     * <p>不能直接用 {@code request.getRemoteAddr()}：经过 Nginx 等反向代理后，
     * 它拿到的是代理的 IP，所有用户会共用同一个限流计数，
     * 表现是"一个人被限流，全公司都登不上"。要按优先级从代理头里取原始 IP。
     *
     * <p>{@code X-Forwarded-For} 可能是逗号分隔的列表（客户端, 代理1, 代理2），
     * 第一个才是真实客户端，所以只取逗号前那段。
     *
     * <p>⚠️ 安全提示：这些头是客户端可以伪造的，未接可信代理时不能当作强身份凭证。
     * 本项目用它只是为了挡脚本，够用。
     */
    private String clientIp(HttpServletRequest request) {
        for (String header : new String[]{"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
