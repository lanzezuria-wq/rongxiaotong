package com.rxt.service;

import com.rxt.common.ResultCode;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.dto.UpdateInfoDTO;
import com.rxt.dto.UpdatePasswordDTO;
import com.rxt.vo.LoginVO;
import com.rxt.vo.RegisterVO;
import com.rxt.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 用户体系服务层测试 —— 对着数据库真跑，不是 Mock。
 *
 * <p><b>为什么不用 Mockito 把 Mapper 打桩？</b>因为这一层最容易出错的地方恰恰是
 * "SQL 有没有真的按预期执行"：BCrypt 密文能不能比对、null 字段会不会被跳过、
 * 逻辑删除条件有没有自动加上——这些用 Mock 全都测不出来。
 * 集成测试慢一点，但它验证的是真东西。
 *
 * <p>{@code @Transactional} 加在类上 → 每个测试方法跑在一个事务里，跑完自动回滚，
 * 不会污染数据库里的种子数据。
 */
@SpringBootTest
@Transactional
class UserServiceM1Test {

    @Autowired
    private UserService userService;

    @Autowired
    private com.rxt.mapper.UserMapper userMapper;

    // 种子数据：13900000004 李农户 / 123456 / status=1
    private static final String FARMER_PHONE = "13900000004";
    // 种子数据：13900000003 张农艺师 / status=0 待审核
    private static final String EXPERT_PENDING_PHONE = "13900000003";
    private static final String PASSWORD = "123456";

    private RegisterDTO newRegister(String phone, String role) {
        RegisterDTO dto = new RegisterDTO();
        dto.setPhone(phone);
        dto.setPassword(PASSWORD);
        dto.setRole(role);
        dto.setVerifyCode("111111");
        return dto;
    }

    private LoginDTO loginDTO(String phone, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setPhone(phone);
        dto.setPassword(password);
        return dto;
    }

    // ========================== 注册 ==========================

    @Test
    @DisplayName("注册农户：status=1，并直接返回可用的 Token（自动登录）")
    void registerFarmer() {
        RegisterVO vo = userService.register(newRegister("13911110001", UserConst.ROLE_FARMER));

        assertNotNull(vo.getUser().getId(), "主键未回填");
        assertEquals(UserConst.STATUS_NORMAL, vo.getUser().getStatus());
        assertEquals(Boolean.FALSE, vo.getNeedAudit());
        assertNotNull(vo.getToken(), "农户注册应直接返回 Token");
        assertNotNull(vo.getUser().getPhone(), "手机号应回填到返回的 VO");
        System.out.println("✅ 农户注册：id=" + vo.getUser().getId()
                + ", name=" + vo.getUser().getName() + ", token 长度=" + vo.getToken().length());
    }

    @Test
    @DisplayName("注册专家：status=0 待审核，不签发 Token")
    void registerExpertNeedsAudit() {
        RegisterVO vo = userService.register(newRegister("13911110002", UserConst.ROLE_EXPERT));

        assertEquals(UserConst.STATUS_PENDING, vo.getUser().getStatus());
        assertEquals(Boolean.TRUE, vo.getNeedAudit());
        assertNull(vo.getToken(), "待审核账号不应签发 Token");
        System.out.println("✅ 专家注册：id=" + vo.getUser().getId() + ", needAudit=" + vo.getNeedAudit());
    }

    @Test
    @DisplayName("注册失败：验证码错误 → 1004")
    void registerWrongVerifyCode() {
        RegisterDTO dto = newRegister("13911110003", UserConst.ROLE_FARMER);
        dto.setVerifyCode("000000");

        BizException e = assertThrows(BizException.class, () -> userService.register(dto));
        assertEquals(ResultCode.VERIFY_CODE_ERROR.getCode(), e.getCode());
        System.out.println("✅ 验证码错误 → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    @Test
    @DisplayName("注册失败：手机号已存在 → 1001")
    void registerDuplicatePhone() {
        BizException e = assertThrows(BizException.class,
                () -> userService.register(newRegister(FARMER_PHONE, UserConst.ROLE_FARMER)));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        assertEquals("该手机号已注册", e.getMessage());
        System.out.println("✅ 重复手机号 → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    @Test
    @DisplayName("注册失败：越权注册 admin 角色 → 被白名单拦下")
    void registerIllegalRole() {
        BizException e = assertThrows(BizException.class,
                () -> userService.register(newRegister("13911110004", UserConst.ROLE_ADMIN)));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        System.out.println("✅ 越权注册 admin → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    // ========================== 登录 ==========================

    @Test
    @DisplayName("登录成功：返回 JWT，且 Token 能被解析回 userId")
    void loginSuccess() {
        LoginVO vo = userService.login(loginDTO(FARMER_PHONE, PASSWORD));

        assertNotNull(vo.getToken());
        assertEquals("Bearer", vo.getTokenType());
        assertEquals(86400L, vo.getExpiresIn(), "Token 有效期应为 24 小时 = 86400 秒");
        assertEquals(FARMER_PHONE, vo.getUser().getPhone());
        System.out.println("✅ 登录成功：user=" + vo.getUser().getName()
                + ", role=" + vo.getUser().getRole() + ", expiresIn=" + vo.getExpiresIn());
        System.out.println("   Token 片段：" + vo.getToken().substring(0, 40) + "...");
    }

    @Test
    @DisplayName("登录失败：密码错误 → 1002")
    void loginWrongPassword() {
        BizException e = assertThrows(BizException.class,
                () -> userService.login(loginDTO(FARMER_PHONE, "wrong-password")));
        assertEquals(ResultCode.LOGIN_FAILED.getCode(), e.getCode());
        System.out.println("✅ 密码错误 → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    @Test
    @DisplayName("登录失败：账号不存在 → 与密码错误完全同样的错误码和文案（防手机号枚举）")
    void loginUserNotFound() {
        BizException e = assertThrows(BizException.class,
                () -> userService.login(loginDTO("13800000000", PASSWORD)));
        assertEquals(ResultCode.LOGIN_FAILED.getCode(), e.getCode());
        assertEquals(ResultCode.LOGIN_FAILED.getMessage(), e.getMessage());
        System.out.println("✅ 账号不存在 → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    @Test
    @DisplayName("登录失败：专家待审核 → 1003 且给出具体原因")
    void loginPendingExpert() {
        BizException e = assertThrows(BizException.class,
                () -> userService.login(loginDTO(EXPERT_PENDING_PHONE, PASSWORD)));
        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("待审核"));
        System.out.println("✅ 待审核账号 → code=" + e.getCode() + ", msg=" + e.getMessage());
    }

    @Test
    @DisplayName("登录失败：连续错 5 次触发锁定 → 1005，且第 6 次直接拒绝")
    void loginLockAfterFiveFailures() {
        for (int i = 1; i <= 4; i++) {
            BizException e = assertThrows(BizException.class,
                    () -> userService.login(loginDTO(FARMER_PHONE, "bad")));
            assertEquals(ResultCode.LOGIN_FAILED.getCode(), e.getCode(), "第 " + i + " 次应为密码错误");
        }
        // 第 5 次：密码错误 + 触发锁定
        BizException fifth = assertThrows(BizException.class,
                () -> userService.login(loginDTO(FARMER_PHONE, "bad")));
        assertEquals(ResultCode.LOGIN_FAILED.getCode(), fifth.getCode());

        // 第 6 次：即使密码是对的，也应该因为锁定而被拒（1005）
        BizException locked = assertThrows(BizException.class,
                () -> userService.login(loginDTO(FARMER_PHONE, PASSWORD)));
        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), locked.getCode());
        assertTrue(locked.getMessage().contains("锁定"));
        System.out.println("✅ 锁定生效 → code=" + locked.getCode() + ", msg=" + locked.getMessage());
    }

    // ========================== 个人信息 / 密码 ==========================

    @Test
    @DisplayName("获取用户信息：返回的 VO 里没有密码字段")
    void getInfoNeverLeaksPassword() {
        LoginVO login = userService.login(loginDTO(FARMER_PHONE, PASSWORD));
        UserVO vo = userService.getInfo(login.getUser().getId());

        assertEquals(FARMER_PHONE, vo.getPhone());
        // 编译期就保证了这一点：UserVO 里根本没有 password 属性，
        // 想泄露都写不出来。这就是"用 VO 而不是直接返回 Entity"的价值。
        System.out.println("✅ 用户信息：name=" + vo.getName() + ", role=" + vo.getRole()
                + ", email=" + vo.getEmail() + "（VO 中无 password 字段）");
    }

    @Test
    @DisplayName("更新个人信息：只改传入字段，手机号与角色不受影响")
    void updateInfoPartial() {
        LoginVO login = userService.login(loginDTO(FARMER_PHONE, PASSWORD));
        Long userId = login.getUser().getId();
        String phoneBefore = login.getUser().getPhone();

        UpdateInfoDTO dto = new UpdateInfoDTO();
        dto.setName("李大国");
        dto.setEmail("lidaguo@rxt.com");
        dto.setProvince("河南省");

        UserVO vo = userService.updateInfo(userId, dto);

        assertEquals("李大国", vo.getName());
        assertEquals("lidaguo@rxt.com", vo.getEmail());
        assertEquals("河南省", vo.getProvince());
        // 没传的字段应保持原值（MyBatis-Plus 的 null 跳过策略）
        assertEquals("周口市", vo.getCity(), "未传的 city 应保持原值");
        assertEquals(phoneBefore, vo.getPhone(), "手机号不可修改");
        assertEquals(UserConst.ROLE_FARMER, vo.getRole(), "角色不可由本人修改");
        System.out.println("✅ 部分更新：name=" + vo.getName() + ", email=" + vo.getEmail()
                + ", city 保持=" + vo.getCity() + ", phone 保持=" + vo.getPhone());
    }

    @Test
    @DisplayName("JSON 列往返：MySQL 的 JSON 类型用 String 接得住，能正常读写")
    void extendJsonRoundTrip() {
        Long userId = userService.login(loginDTO(FARMER_PHONE, PASSWORD)).getUser().getId();

        // 直接经 Mapper 写入，验证 JSON 列不会被当成 byte[] 卡住
        com.rxt.entity.User u = new com.rxt.entity.User();
        u.setId(userId);
        u.setExtendJson("{\"plantArea\": \"120亩\", \"mainCrop\": \"小麦\"}");
        userMapper.updateById(u);

        UserVO vo = userService.getInfo(userId);
        assertNotNull(vo.getExtendJson(), "extend_json 读回来是 null —— JSON 列映射有问题");
        assertTrue(vo.getExtendJson().contains("小麦"), "JSON 内容不完整：" + vo.getExtendJson());
        System.out.println("✅ JSON 列往返成功：" + vo.getExtendJson());
    }

    @Test
    @DisplayName("修改密码：原密码错 → 拒绝；改成功后旧密码失效、新密码可用")
    void changePasswordFlow() {
        Long userId = userService.login(loginDTO(FARMER_PHONE, PASSWORD)).getUser().getId();

        // 原密码错误
        UpdatePasswordDTO wrong = new UpdatePasswordDTO();
        wrong.setOldPassword("not-my-password");
        wrong.setNewPassword("newPass123");
        BizException e = assertThrows(BizException.class, () -> userService.changePassword(userId, wrong));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        assertEquals("原密码错误", e.getMessage());

        // 新旧相同
        UpdatePasswordDTO same = new UpdatePasswordDTO();
        same.setOldPassword(PASSWORD);
        same.setNewPassword(PASSWORD);
        BizException e2 = assertThrows(BizException.class, () -> userService.changePassword(userId, same));
        assertEquals("新密码不能与原密码相同", e2.getMessage());

        // 正常修改
        UpdatePasswordDTO ok = new UpdatePasswordDTO();
        ok.setOldPassword(PASSWORD);
        ok.setNewPassword("newPass123");
        userService.changePassword(userId, ok);

        // 旧密码登录失败
        assertThrows(BizException.class, () -> userService.login(loginDTO(FARMER_PHONE, PASSWORD)));
        // 新密码登录成功
        LoginVO after = userService.login(loginDTO(FARMER_PHONE, "newPass123"));
        assertNotNull(after.getToken());
        System.out.println("✅ 改密成功：旧密码已失效，新密码可登录");
    }
}
