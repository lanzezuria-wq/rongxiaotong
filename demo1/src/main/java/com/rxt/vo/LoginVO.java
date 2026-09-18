package com.rxt.vo;

import com.rxt.entity.User;
import lombok.Data;

/**
 * 登录成功出参 —— POST /api/user/login 的响应体。
 *
 * <p>为什么要做成一个对象而不是直接返回一个 Token 字符串？
 * 因为 SRS 表12 的操作流程是"返回 Token 写入 localStorage → 跳转首页"，
 * 跳转后首页要立刻显示用户名和角色。如果只给 Token，前端还得再发一次
 * GET /api/user/info 才能拿到用户信息——多一次往返，还会闪一下空白。
 * 把 user 一起带上，登录一次到位。
 */
@Data
public class LoginVO {

    /** JWT 字符串，前端存进 localStorage */
    private String token;

    /** 固定 Bearer，前端拼请求头时用：Authorization: Bearer <token> */
    private String tokenType;

    /** 有效期（秒）。需求规定 24 小时 = 86400 */
    private Long expiresIn;

    /** 当前登录用户信息（不含密码） */
    private UserVO user;

    public static LoginVO of(String token, long expiresInSeconds, User user) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(expiresInSeconds);
        vo.setUser(UserVO.from(user));
        return vo;
    }
}
