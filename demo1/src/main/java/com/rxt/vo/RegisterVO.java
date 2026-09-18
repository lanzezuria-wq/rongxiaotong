package com.rxt.vo;

import com.rxt.entity.User;
import lombok.Data;

/**
 * 注册出参 —— POST /api/user/register 的响应体。
 *
 * <p><b>为什么要单独做一个 VO？</b>SRS 表11 说注册成功后"自动登录，跳转首页"，
 * 但同一张表的业务规则又说"专家注册后 status=0（待审核）"——待审核的账号不能登录。
 * 两条要求冲突，必须用数据告诉前端"现在该跳哪"：
 *
 * <ul>
 *   <li>农户注册 → {@code needAudit=false}，{@code token} 有值 → 前端直接存 Token 跳首页（自动登录）；</li>
 *   <li>专家注册 → {@code needAudit=true}，{@code token=null} → 前端弹"注册成功，请等待管理员审核"，跳登录页。</li>
 * </ul>
 *
 * <p>如果复用 LoginVO，专家那条路的 token 为 null，前端只能靠"token 是不是空"
 * 去猜业务含义，很容易写错。显式给一个 {@code needAudit} 标志位，语义就自解释了。
 */
@Data
public class RegisterVO {

    /** 新用户信息（不含密码） */
    private UserVO user;

    /** 自动登录用的 Token；需要审核时为 null */
    private String token;

    /** 是否需要等待管理员审核（专家为 true） */
    private Boolean needAudit;

    public static RegisterVO of(User user, String token) {
        RegisterVO vo = new RegisterVO();
        vo.setUser(UserVO.from(user));
        vo.setToken(token);
        vo.setNeedAudit(token == null);
        return vo;
    }
}
