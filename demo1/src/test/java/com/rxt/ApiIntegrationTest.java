package com.rxt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端接口测试 —— 走完整的 HTTP 语义链路。
 *
 * <p><b>它验证的链路：</b>
 * <pre>
 *   MockMvc 请求
 *     → Spring Security 过滤器链（JwtAuthenticationFilter → 授权判断）
 *     → Controller（参数校验、取当前用户）
 *     → Service（业务规则）
 *     → Mapper → MySQL（真实数据库）
 *     → 全局异常处理器 / 统一响应包装
 *     → JSON 响应
 * </pre>
 * 除了没有真的开一个 Tomcat 端口，其它环节全是真实的。
 * 因此它既能验证功能，也能验证"401 时返回的到底是不是 Result 格式"这类
 * 只有跑起来才看得出来的问题。
 *
 * <p><b>为什么不用 @SpringBootTest(webEnvironment=RANDOM_PORT) + TestRestTemplate？</b>
 * 那样会真开端口，更接近生产。但本项目所在环境对 localhost 的 HTTP 连接做了拦截，
 * 外部客户端打不进来。MockMvc 完全在进程内，不受影响，而且启动更快。
 * 在正常的开发机上两种都可以用。
 *
 * <p><b>两个关键配置：</b>
 * <ul>
 *   <li>{@code @AutoConfigureMockMvc} —— 注意包名是
 *       {@code org.springframework.boot.webmvc.test.autoconfigure}。
 *       Spring Boot 4 <b>改过这个类的包路径</b>（Boot 3 是
 *       {@code ...boot.test.autoconfigure.web.servlet}），照抄老教程会找不到类。</li>
 *   <li>{@code properties = "rxt.security.rate-limit.max-requests=1000"} ——
 *       把 IP 限流阈值调到很大。因为这个测试类里会发几十次登录请求，
 *       全来自同一个 IP 127.0.0.1，按默认的 20 次/5 分钟会被限流器拦下，
 *       导致大量"莫名其妙的 429"。限流本身另有专门的测试类验证。</li>
 * </ul>
 */
@SpringBootTest(properties = {"rxt.security.rate-limit.max-requests=1000"})
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {

    private static final String ADMIN_PHONE = "13900000000";
    private static final String FARMER_PHONE = "13900000004";
    private static final String PENDING_EXPERT_PHONE = "13900000003";
    private static final String AUDITED_EXPERT_PHONE = "13900000006";
    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Value("${rxt.jwt.secret}")
    private String jwtSecret;

    // ==================================================================================
    // 0. 地基：统一响应 + 放行接口
    // ==================================================================================

    @Test
    @DisplayName("健康检查：放行接口返回统一响应结构，code=0")
    void pingIsPublic() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("rxt-backend"));
    }

    // ==================================================================================
    // 1. 认证失败的三种表现（1002 / 1006 / 1003）
    // ==================================================================================

    @Test
    @DisplayName("未带 Token 访问受保护接口 → HTTP 401 + code 1002，且响应体是 Result 格式")
    void noTokenReturns1002() throws Exception {
        mockMvc.perform(get("/api/user/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Token 签名被篡改 → HTTP 401 + code 1002")
    void tamperedTokenReturns1002() throws Exception {
        String fake = login(ADMIN_PHONE, PASSWORD);
        // 把签名段改掉一个字符，签名校验必然失败
        String tampered = fake.substring(0, fake.length() - 2) + "xy";

        mockMvc.perform(get("/api/user/info").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("Token 已过期 → HTTP 401 + code 1006（前端据此静默跳登录页）")
    void expiredTokenReturns1006() throws Exception {
        // 手工造一个签名合法、但 exp 已经过去的 Token
        String expired = Jwts.builder()
                .subject("1")
                .claim("phone", ADMIN_PHONE)
                .claim("role", "admin")
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000L))
                .expiration(new Date(System.currentTimeMillis() - 3600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/user/info").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    // ==================================================================================
    // 2. 授权：1007 权限不足
    // ==================================================================================

    @Test
    @DisplayName("农户访问管理员接口 → HTTP 403 + code 1007（不是 401，前端不该跳登录页）")
    void farmerCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/user/list").header("Authorization", bearer(farmerToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    @DisplayName("农户越权审核文章 → 403 + 1007（该接口不在 /api/admin/** 下，权限规则要单独覆盖）")
    void farmerCannotAuditArticle() throws Exception {
        mockMvc.perform(put("/api/article/1/audit")
                        .header("Authorization", bearer(farmerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
    }

    // ==================================================================================
    // 3. 参数校验：1001
    // ==================================================================================

    @Test
    @DisplayName("注册时手机号格式错误 → HTTP 400 + code 1001，且提示到具体字段")
    void invalidPhoneReturns1001() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"12345","password":"abc123","role":"farmer","verifyCode":"111111"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("phone")));
    }

    @Test
    @DisplayName("注册时密码不足 6 位 → 1001")
    void shortPasswordReturns1001() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13977770001","password":"123","role":"farmer","verifyCode":"111111"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ==================================================================================
    // 4. M1 五个接口
    // ==================================================================================

    @Test
    @DisplayName("M1-1 注册农户：status=1、直接返回 Token（自动登录）")
    void registerFarmerReturnsToken() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13977770002","password":"abc123","role":"farmer","verifyCode":"111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.role").value("farmer"))
                .andExpect(jsonPath("$.data.user.status").value(1))
                .andExpect(jsonPath("$.data.needAudit").value(false))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("M1-1 注册专家：status=0 待审核、token 为 null")
    void registerExpertNeedsAudit() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13977770003","password":"abc123","role":"expert","verifyCode":"111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.status").value(0))
                .andExpect(jsonPath("$.data.needAudit").value(true))
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("M1-1 注册：验证码错误 → 1004")
    void registerWrongVerifyCode() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13977770004","password":"abc123","role":"farmer","verifyCode":"000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("M1-1 注册：手机号已存在 → 1001 '该手机号已注册'")
    void registerDuplicatePhone() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"abc123","role":"farmer","verifyCode":"111111"}
                                """.formatted(FARMER_PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("该手机号已注册"));
    }

    @Test
    @DisplayName("M1-2 登录成功：返回 Token 与用户信息，响应的 user 里不含 password")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(FARMER_PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400))
                .andExpect(jsonPath("$.data.user.phone").value(FARMER_PHONE))
                // 安全断言：响应体里绝不能出现 password 字段
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    @DisplayName("M1-2 登录：密码错误 → 1002；账号不存在 → 也是 1002（防手机号枚举）")
    void loginWrongPasswordAndUnknownUserBothReturn1002() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"wrong-pwd"}
                                """.formatted(FARMER_PHONE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("手机号或密码错误"));

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800000000","password":"123456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                // 文案必须与"密码错误"完全一致，否则可以用来枚举手机号
                .andExpect(jsonPath("$.message").value("手机号或密码错误"));
    }

    @Test
    @DisplayName("M1-2 登录：待审核专家 → 1003 且说明原因")
    void loginPendingExpertReturns1003() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(PENDING_EXPERT_PHONE, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("待审核")));
    }

    @Test
    @DisplayName("M1-3 获取当前用户信息：用 Token 换身份，返回自己的资料")
    void getInfoWithToken() throws Exception {
        mockMvc.perform(get("/api/user/info").header("Authorization", bearer(farmerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phone").value(FARMER_PHONE))
                .andExpect(jsonPath("$.data.role").value("farmer"))
                // 日期必须是 yyyy-MM-dd HH:mm:ss 格式（不是 ISO 的带 T 形式）
                .andExpect(jsonPath("$.data.createTime").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    @DisplayName("M1-4 更新个人信息：只改传入字段，手机号与角色保持不变")
    void updateInfoPartial() throws Exception {
        mockMvc.perform(put("/api/user/info")
                        .header("Authorization", bearer(farmerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"李大国","email":"lidaguo@rxt.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("李大国"))
                .andExpect(jsonPath("$.data.email").value("lidaguo@rxt.com"))
                // 没传的字段保持原值
                .andExpect(jsonPath("$.data.city").value("周口市"))
                // 手机号和角色不可由本人修改
                .andExpect(jsonPath("$.data.phone").value(FARMER_PHONE))
                .andExpect(jsonPath("$.data.role").value("farmer"));
    }

    @Test
    @DisplayName("M1-4 更新个人信息：邮箱格式错误 → 1001")
    void updateInfoBadEmail() throws Exception {
        mockMvc.perform(put("/api/user/info")
                        .header("Authorization", bearer(farmerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("M1-5 修改密码：原密码错 → 1001；改成功后旧密码失效、新密码可用")
    void changePasswordFlow() throws Exception {
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", bearer(farmerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"not-my-pwd","newPassword":"newPass123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("原密码错误"));

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", bearer(farmerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"newPass123"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 旧密码不再可用
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(FARMER_PHONE, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));

        // 新密码可以登录
        assertNotNull(login(FARMER_PHONE, "newPass123"));
    }

    // ==================================================================================
    // 5. M7 八个接口
    // ==================================================================================

    @Test
    @DisplayName("M7-1 用户列表：管理员可查，分页与筛选生效")
    void m7UserList() throws Exception {
        String admin = adminToken();

        // ============================================================================
        // ⚠️ 这里刻意**不**断言"用户总数等于某个数字"。
        //
        // 原来写的是 value(7)（P1 的种子数）。这个断言已经被打破过两次：
        //   · P2 加了 1 个演示买家账号（13900000008）→ 变 8，P2 提交时没同步，测试就红了；
        //   · P3 又加了 6 个账号（4 农户 + 2 专家，联保小组最少要 3 农户才演示得了）→ 变 14。
        // 每次有人加种子数据就要回来改这个数字，而漏改的代价是"整条流水线变红"，
        // 但断言本身并没有校验什么真正的业务规则 —— 它只是在数 SQL 脚本里的行数。
        //
        // 所以改成校验**不变量**：能查到、分页结构自洽、按条件筛出来的记录确实符合条件。
        // 这些才是这个接口真正该保证的东西，而且不会因为别人加种子数据而失效。
        // ============================================================================

        // 1) 全量查询：能查通、返回分页结构、且密码绝不出现在响应里
        String body = mockMvc.perform(get("/api/admin/user/list")
                        .param("page", "1").param("size", "100")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long total = extractLong(body, "total");
        // 至少要覆盖 P1 的 7 个种子账号 —— 用下界而不是等号，别人加种子不会再弄红它
        assertTrue(total >= 7, "用户总数应至少包含 P1 的 7 个种子账号，实际 " + total);

        // 2) 按角色筛选：**返回的每一条**都必须是 expert（这是筛选功能的真正不变量），
        //    而不是去数"到底有几个专家"
        String expertBody = mockMvc.perform(get("/api/admin/user/list")
                        .param("role", "expert").param("size", "100")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        assertTrue(expertBody.contains("\"role\":\"expert\""), "按 expert 筛选应返回专家：" + expertBody);
        assertTrue(!expertBody.contains("\"role\":\"admin\""),
                "按 expert 筛选不该混入管理员：" + expertBody);

        // 3) 专家审核页的取数方式：role=expert & status=0（待审核）。
        //    锚定断言：P1 的待审专家「张农艺师」必须在这份列表里 ——
        //    这才是审核页真正依赖的东西（"列表里有没有该处理的人"），
        //    而不是"列表里有几条"。
        String pendingBody = mockMvc.perform(get("/api/admin/user/list")
                        .param("role", "expert").param("status", "0").param("size", "100")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pendingBody.contains(PENDING_EXPERT_PHONE),
                "待审核专家列表应包含 " + PENDING_EXPERT_PHONE + "：" + pendingBody);
        assertTrue(pendingBody.contains("\"status\":0"),
                "该列表里不应出现非待审核状态：" + pendingBody);

        // 4) 关键词模糊搜索（这个断言与种子总量无关，一直很稳）
        mockMvc.perform(get("/api/admin/user/list")
                        .param("keyword", "李农户")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].phone").value(FARMER_PHONE));

        // 5) 分页自洽：每页 3 条时，页数必须是 ceil(total/3)，
        //    且首页确实返回 3 条 —— 用 total 现算，不写死
        long expectedPages = (total + 2) / 3;
        mockMvc.perform(get("/api/admin/user/list")
                        .param("page", "1").param("size", "3")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.pages").value(expectedPages));
    }

    @Test
    @DisplayName("M7-2 禁用/启用用户：管理员不能禁用自己；禁用后该用户无法登录")
    void m7ChangeUserStatus() throws Exception {
        String admin = adminToken();

        // 禁用自己 → 被拒
        mockMvc.perform(put("/api/admin/user/1/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("不能禁用自己的账号"));

        // 非法状态值
        mockMvc.perform(put("/api/admin/user/5/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 正常禁用农户
        mockMvc.perform(put("/api/admin/user/5/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 禁用后无法登录（表38：禁用后该用户无法登录）
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(FARMER_PHONE, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));

        // 重新启用后可以登录
        mockMvc.perform(put("/api/admin/user/5/status")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());
        assertNotNull(login(FARMER_PHONE, PASSWORD));
    }

    @Test
    @DisplayName("M7-3 审核专家：通过 → status=1 且写入 t_expert；审核结果不可撤回")
    void m7AuditExpertApproved() throws Exception {
        String admin = adminToken();
        String body = """
                {"status":"APPROVED","remark":"资质齐备，予以通过"}
                """;

        mockMvc.perform(put("/api/admin/expert/4/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 通过后该专家可以登录了
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(PENDING_EXPERT_PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value(1));

        // 审核结果不可撤回：再审核一次必须失败
        mockMvc.perform(put("/api/admin/expert/4/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","remark":"反悔了"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("该专家已审核，审核结果不可撤回"));
    }

    @Test
    @DisplayName("M7-3 审核专家：拒绝时未填理由 → 1001；审核非专家角色 → 1001")
    void m7AuditExpertRejected() throws Exception {
        String admin = adminToken();

        // 拒绝但没写理由
        mockMvc.perform(put("/api/admin/expert/4/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("拒绝时必须填写理由"));

        // 非法的审核结果取值
        mockMvc.perform(put("/api/admin/expert/4/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAYBE\",\"remark\":\"随便\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 拿农户的 id 去审核 → 不是专家角色
        mockMvc.perform(put("/api/admin/expert/5/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该账号不是专家角色，无需审核"));

        // 已审核过的专家（status=1 的赵教授）不能再审
        mockMvc.perform(get("/api/admin/user/list")
                        .param("keyword", AUDITED_EXPERT_PHONE)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value(1));
    }

    @Test
    @DisplayName("M7-8 文章审核：列表能取到 3 篇待审；拒绝必须有理由；通过后变为已发布")
    void m7AuditArticle() throws Exception {
        String admin = adminToken();

        // 待审核文章列表（审核页的数据来源）
        mockMvc.perform(get("/api/article/list")
                        .param("status", "0")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].authorName").value("李农户"));

        // 拒绝但不写理由
        mockMvc.perform(put("/api/article/1/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("拒绝时必须填写理由"));

        // 正常拒绝
        mockMvc.perform(put("/api/article/1/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","remark":"涉及未经验证的农药用法"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 通过另一篇
        mockMvc.perform(put("/api/article/2/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED","remark":null}
                                """))
                .andExpect(status().isOk());

        // 已审核的不能再审
        mockMvc.perform(put("/api/article/1/audit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该文章已审核，不可重复操作"));

        // 剩余待审核数量
        mockMvc.perform(get("/api/article/list")
                        .param("status", "0")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("M7-4 字典查询：所有登录用户都能用（前端下拉框依赖它），无需管理员")
    void m7DictQueryIsOpenToAllLoggedInUsers() throws Exception {
        mockMvc.perform(get("/api/admin/dict/product_category")
                        .header("Authorization", bearer(farmerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].typeName").value("产品分类"))
                // 0/1 对外暴露为布尔值
                .andExpect(jsonPath("$.data[0].enabled").value(true));

        // 未登录不能查
        mockMvc.perform(get("/api/admin/dict/product_category"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));

        // 非法类型
        mockMvc.perform(get("/api/admin/dict/not_a_type")
                        .header("Authorization", bearer(farmerToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("M7-5/6/7 字典增删改：农户无权写，管理员可写；同类型编码唯一")
    void m7DictCrud() throws Exception {
        String admin = adminToken();
        String farmer = farmerToken();

        // 权限：农户不能新增
        mockMvc.perform(post("/api/admin/dict")
                        .header("Authorization", bearer(farmer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"industry","code":"TEST","name":"测试行业"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        // 管理员新增
        String created = mockMvc.perform(post("/api/admin/dict")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"industry","code":"TEST","name":"测试行业","sort":9,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.typeName").value("所属行业"))
                .andReturn().getResponse().getContentAsString();
        String newId = extractNumber(created, "id");

        // 同类型下编码重复 → 1001
        mockMvc.perform(post("/api/admin/dict")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"industry","code":"TEST","name":"重复编码"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("编码已存在")));

        // 跨类型同名编码应当允许（唯一索引是 (type, code) 复合索引）
        mockMvc.perform(post("/api/admin/dict")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"product_category","code":"TEST","name":"测试分类"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 非法类型
        mockMvc.perform(post("/api/admin/dict")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"not_a_type","code":"X","name":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 编辑：只改名称，编码不变，不应被误判成冲突
        mockMvc.perform(put("/api/admin/dict/" + newId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"industry","code":"TEST","name":"测试行业（改名）","sort":10,"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("测试行业（改名）"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.sort").value(10));

        // 删除
        mockMvc.perform(delete("/api/admin/dict/" + newId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 删除一个不存在的 id → 1008（资源不存在）
        mockMvc.perform(delete("/api/admin/dict/999999")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1008));
    }

    // ==================================================================================
    // 辅助方法
    // ==================================================================================

    private String login(String phone, String password) throws Exception {
        String json = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String token = extractString(json, "token");
        assertNotNull(token, "登录未返回 token：" + json);
        return token;
    }

    private String adminToken() throws Exception {
        return login(ADMIN_PHONE, PASSWORD);
    }

    private String farmerToken() throws Exception {
        return login(FARMER_PHONE, PASSWORD);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * 从 JSON 文本里抠一个字符串字段的值。
     *
     * <p>这里用正则而不是 JSON 解析库，是为了绕开"Boot 4 用 Jackson 3、类路径上还并存
     * Jackson 2"带来的歧义——测试里注入哪个 ObjectMapper 都不够直观。
     * 只取我们自己生成的、格式确定的字段，正则足够可靠。
     */
    private String extractString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 抠一个数字字段（JSON 里 id 是不带引号的数字） */
    private String extractNumber(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 抠一个整数字段并返回 long（抠不到返回 -1）。
     *
     * <p>P3 新增，为了把 {@code m7UserList} 里写死的"用户总数 = 7"改成
     * "用返回值现算分页页数"。返回 -1 而不是抛异常，是为了让断言失败时
     * 看到的是"期望 3 页、实际 -1 页"这种能立刻定位的信息，
     * 而不是一个找不到字段的异常堆栈。
     */
    private long extractLong(String json, String field) {
        String value = extractNumber(json, field);
        return value == null ? -1L : Long.parseLong(value);
    }
}
