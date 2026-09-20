package com.rxt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * P3 端到端接口测试 —— M4 融资服务 + M6 银行端 + M5 专家助力。
 *
 * <p><b>它和 P1 的 {@link ApiIntegrationTest} 是同一套打法：</b>
 * MockMvc 走完整链路（Security 过滤器 → Controller 参数校验 → Service 业务规则
 * → Mapper → 真实 MySQL → 全局异常 → 统一响应），只是不开真实端口。
 * 覆盖 P3 交付的 23 个接口与它们的业务边界。
 *
 * <p><b>为什么这个测试类比 P1 的更需要"走真实数据库"？</b>
 * 因为 P3 的 bug 大多不在 Java 代码里，而在 Java 与数据库的接缝处。实测踩到过一个：
 * {@code t_financing_apply.usage} 这个列名是 MySQL 关键字，
 * MyBatis-Plus 生成的 {@code INSERT INTO t (..., usage, ...)} <b>语法直接报错</b>，
 * 而建表脚本里写了反引号所以 DDL 是好的 —— 这类问题任何 mock 掉 DB 的单测都发现不了。
 *
 * <p><b>两个配置要点：</b>
 * <ul>
 *   <li>{@code rxt.security.rate-limit.max-requests=1000} —— 本类会发几十次登录请求，
 *       全来自 127.0.0.1，按默认 20 次/5 分钟会被 IP 限流拦下，
 *       表现是一堆莫名其妙的 429。限流另有专门测试验证。</li>
 *   <li>{@code @Transactional} —— 每个用例结束回滚，用例之间互不污染。
 *       所以下面每个用例都自己准备前置数据（提交申请、建小组…），不依赖执行顺序。</li>
 * </ul>
 *
 * <p><b>为什么断言里基本不校验中文文案？</b>
 * 因为 MvcResult 的响应体编码受平台默认字符集影响（本机是 GBK），
 * 断言中文容易出现"逻辑对但编码对不上"的假失败。
 * 所以这里只断言<b>业务码 + HTTP 状态 + 数值 + 结构</b>；
 * 文案的准确性交给真实进程级的端到端脚本（tools/p3-e2e-test.py）去验。
 *
 * <p><b>一个所有断言都要小心的坑：响应字段是按字母序输出的。</b>
 * 例如 {@code GET /api/financing/{id}} 的详情里既有申请自己的 {@code status}，
 * 又有嵌套 {@code group} 对象里的 {@code status}，而 group 字母序在前 ——
 * "取第一个 status"会拿到小组的状态。所以下面凡是涉及状态的断言，
 * 要么用列表接口的筛选参数（无歧义），要么用它自己的叶子字段。
 */
@SpringBootTest(properties = {"rxt.security.rate-limit.max-requests=1000"})
@AutoConfigureMockMvc
@Transactional
class P3ApiIntegrationTest {

    private static final String PASSWORD = "123456";

    /** 刘四海：种子里已有一条「待匹配」的融资申请（120000 元），可用来加入小组 */
    private static final String FARMER_APPLIED = "13900000013";
    /** 孙小满：种子农户，没有申请，用来测"从零提交申请" */
    private static final String FARMER_FRESH = "13900000014";
    /** 李农户：已在演示小组 LB-DEMO-0001（已成型）里 */
    private static final String FARMER_IN_DEMO_GROUP = "13900000004";
    private static final String BANK_PHONE = "13900000002";
    /** 赵教授：P1 种子里已审核通过的专家，其 t_expert.id = 1 */
    private static final String EXPERT_PHONE = "13900000006";
    private static final long EXPERT_ID = 1L;
    private static final String ADMIN_PHONE = "13900000000";

    @Autowired
    private MockMvc mockMvc;

    /** 带缓存的登录，避免同一次用例里反复登录（也少占限流额度） */
    private final Map<String, String> tokenCache = new HashMap<>();

    // ==================================================================================
    // 一、鉴权与越权（先验门，再验屋里）
    // ==================================================================================

    @Test
    @DisplayName("M4：未带 Token 访问融资列表 → 401 + 1002")
    void financingRequiresAuth() throws Exception {
        Resp r = getReq("/api/financing/list", null);
        assertEquals(401, r.status);
        assertEquals(1002, r.code());
    }

    @Test
    @DisplayName("M6：农户访问银行端统计 → 403 + 1007（不是 401，前端不该跳登录页）")
    void farmerCannotReachBankApi() throws Exception {
        Resp r = getReq("/api/bank/stats", farmer(FARMER_APPLIED));
        assertEquals(403, r.status);
        assertEquals(1007, r.code());
    }

    @Test
    @DisplayName("M5：游客可访问专家列表与文章详情与文章列表（permitAll）")
    void expertListAndArticleDetailArePublic() throws Exception {
        assertEquals(200, getReq("/api/expert/list", null).status);
        assertEquals(200, getReq("/api/article/list", null).status);
    }

    // ==================================================================================
    // 二、M4 提交融资申请
    // ==================================================================================

    @Test
    @DisplayName("M4：农户提交申请成功，返回 id 与 matchedGroups 数组")
    void applyFinancingSuccess() throws Exception {
        Resp r = postReq("/api/financing/apply", farmer(FARMER_FRESH), """
                {"amount":60000,"usage":"SEED","industry":"PLANTING",
                 "province":"山东省","city":"潍坊市","loanTerm":"M12",
                 "description":"测试提交"}""");
        assertEquals(200, r.status);
        assertEquals(0, r.code(), r.body);
        assertTrue(r.longField("id") > 0);
        assertTrue(r.body.contains("\"matchedGroups\":["), "应返回 matchedGroups 数组：" + r.body);
    }

    @Test
    @DisplayName("M4：契约枚举值（小写 expand/planting/12）也能提交 —— 字典与契约两套口径都接受")
    void applyAcceptsContractEnum() throws Exception {
        Resp r = postReq("/api/financing/apply", farmer(FARMER_FRESH), """
                {"amount":60000,"usage":"expand","industry":"planting",
                 "province":"山东省","city":"潍坊市","loanTerm":"12",
                 "description":"契约枚举"}""");
        assertEquals(0, r.code(), r.body);
    }

    @Test
    @DisplayName("M4：已有进行中申请时重复提交 → 3001")
    void applyDuplicateActiveReturns3001() throws Exception {
        Resp r = postReq("/api/financing/apply", farmer(FARMER_APPLIED), """
                {"amount":60000,"usage":"SEED","industry":"PLANTING",
                 "province":"山东省","city":"潍坊市","loanTerm":"M12",
                 "description":"重复"}""");
        assertEquals(400, r.status);
        assertEquals(3001, r.code());
    }

    @Test
    @DisplayName("M4：金额低于下限 → 1001（Bean Validation 拦下）")
    void applyAmountTooSmallReturns1001() throws Exception {
        Resp r = postReq("/api/financing/apply", farmer(FARMER_FRESH), """
                {"amount":5000,"usage":"SEED","industry":"PLANTING",
                 "province":"山东省","city":"潍坊市","loanTerm":"M12",
                 "description":"金额越界"}""");
        assertEquals(400, r.status);
        assertEquals(1001, r.code());
    }

    @Test
    @DisplayName("M4：资金用途不在字典也不在契约枚举 → 1001")
    void applyInvalidUsageReturns1001() throws Exception {
        Resp r = postReq("/api/financing/apply", farmer(FARMER_FRESH), """
                {"amount":60000,"usage":"NOT_A_CODE","industry":"PLANTING",
                 "province":"山东省","city":"潍坊市","loanTerm":"M12",
                 "description":"字典校验"}""");
        assertEquals(400, r.status);
        assertEquals(1001, r.code());
    }

    @Test
    @DisplayName("M4：银行角色提交融资申请 → 3004（仅农户可申请）")
    void bankCannotApplyFinancing() throws Exception {
        Resp r = postReq("/api/financing/apply", bank(), """
                {"amount":60000,"usage":"SEED","industry":"PLANTING",
                 "province":"山东省","city":"潍坊市","loanTerm":"M12",
                 "description":"角色不对"}""");
        assertEquals(403, r.status);
        assertEquals(3004, r.code());
    }

    @Test
    @DisplayName("M4：我的申请列表返回分页结构，且带 statusName 中文状态")
    void myApplyListHasPageShape() throws Exception {
        Resp r = getReq("/api/financing/list", farmer(FARMER_APPLIED));
        assertEquals(0, r.code());
        for (String key : new String[]{"records", "total", "page", "size", "pages"}) {
            assertTrue(r.body.contains("\"" + key + "\""), "缺少分页字段 " + key + "：" + r.body);
        }
        assertTrue(r.body.contains("\"statusName\""), "应返回 statusName：" + r.body);
    }

    @Test
    @DisplayName("M4：查看别人的申请详情 → 403 + 1007")
    void cannotReadOthersApply() throws Exception {
        long applyId = firstApplyIdOf(farmer(FARMER_APPLIED));
        Resp r = getReq("/api/financing/" + applyId, farmer(FARMER_IN_DEMO_GROUP));
        assertEquals(403, r.status);
        assertEquals(1007, r.code());
    }

    @Test
    @DisplayName("M4：申请详情包含 description / group / approval 三个详情字段")
    void applyDetailHasExtraFields() throws Exception {
        long applyId = firstApplyIdOf(farmer(FARMER_APPLIED));
        Resp r = getReq("/api/financing/" + applyId, farmer(FARMER_APPLIED));
        assertEquals(0, r.code());
        assertTrue(r.body.contains("\"description\""), r.body);
        assertTrue(r.body.contains("\"group\""), r.body);
        assertTrue(r.body.contains("\"approval\""), r.body);
    }

    // ==================================================================================
    // 三、M4 联保小组：创建 → 加入 → 成型 → 取消
    // ==================================================================================

    @Test
    @DisplayName("M4：发起小组成功；发起人自动入组，编号按 LB- 回填，状态为招募中")
    void createGroupSuccess() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);

        Resp detail = getReq("/api/financing/group/" + groupId, farmer(FARMER_FRESH));
        assertEquals(0, detail.code(), detail.body);
        assertEquals(1, detail.num("currentMembers"));
        assertEquals(0, detail.num("status"));
        assertEquals(2, detail.num("remainingToForm"));
        assertEquals(10, detail.num("maxMembers"));
        assertTrue(detail.str("groupNo").startsWith("LB-"),
                "编号应为 LB- 开头：" + detail.str("groupNo"));
    }

    @Test
    @DisplayName("M4：没有「待匹配」申请时发起小组 → 1001（不允许创建空小组）")
    void createGroupWithoutApplyFails() throws Exception {
        // 李农户的申请已挂在演示小组上（状态=已匹配），所以没有待匹配的申请
        Resp r = postReq("/api/financing/create-group", farmer(FARMER_IN_DEMO_GROUP), """
                {"industry":"PLANTING","province":"河南省","city":"周口市"}""");
        assertEquals(400, r.status);
        assertEquals(1001, r.code(), r.body);
    }

    @Test
    @DisplayName("M4：加入小组后人数 +1，且 totalAmount 等于成员金额之和（冗余列与明细一致）")
    void joinGroupKeepsAggregateConsistent() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        String tokenA = farmer(FARMER_APPLIED);
        joinGroup(groupId, firstApplyIdOf(tokenA), tokenA);

        Resp detail = getReq("/api/financing/group/" + groupId, farmer(FARMER_FRESH));
        assertEquals(2, detail.num("currentMembers"));
        assertEquals(0, detail.num("status"), "2 人还没成型");
        assertEquals(1, detail.num("remainingToForm"));

        // 校验不变量：冗余的 totalAmount 必须等于明细之和。
        // 这比断言具体数字更有价值 —— 它验证的是"冗余列不会偏离明细"，
        // 而这个不变量才是后续任何成员变动都必须保持的东西。
        double total = detail.num("totalAmount");
        double sum = 0;
        Matcher m = Pattern.compile("\"applyAmount\"\\s*:\\s*([0-9.]+)").matcher(detail.body);
        while (m.find()) {
            sum += Double.parseDouble(m.group(1));
        }
        assertEquals(sum, total, 0.01, "totalAmount 与成员金额之和不一致");
    }

    @Test
    @DisplayName("M4：重复加入同一小组 → 400 且提示「已在该小组中」")
    void duplicateJoinRejected() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        String token = farmer(FARMER_APPLIED);
        long applyId = firstApplyIdOf(token);
        joinGroup(groupId, applyId, token);

        Resp again = postReq("/api/financing/join", token,
                "{\"groupId\":%d,\"applyId\":%d}".formatted(groupId, applyId));
        assertEquals(400, again.status);
        // 1001 且文案指向"已在该小组中"（而不是把申请状态的枚举名抛给用户）
        assertEquals(1001, again.code());
    }

    @Test
    @DisplayName("M4：拿别人的申请加入小组 → 403 + 1007")
    void cannotJoinWithOthersApply() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        long othersApply = firstApplyIdOf(farmer(FARMER_APPLIED));

        Resp r = postReq("/api/financing/join", farmer(FARMER_IN_DEMO_GROUP),
                "{\"groupId\":%d,\"applyId\":%d}".formatted(groupId, othersApply));
        assertEquals(403, r.status);
        assertEquals(1007, r.code());
    }

    @Test
    @DisplayName("M4：自己已在的小组会从「可加入」列表里排除")
    void availableGroupsExcludesOwn() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        Resp r = getReq("/api/financing/groups", farmer(FARMER_FRESH));
        assertEquals(0, r.code());
        assertTrue(!r.body.contains("\"id\":" + groupId + ","),
                "自己已在的小组不该出现在可加入列表：" + r.body);
    }

    @Test
    @DisplayName("M4：凑满 3 人 → 小组自动成型，组内申请联动为「待银行审批」")
    void groupFormsAtThreeMembers() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        String tokenA = farmer(FARMER_APPLIED);
        long applyA = firstApplyIdOf(tokenA);
        joinGroup(groupId, applyA, tokenA);

        // 第三个人：新注册一个农户再走一遍 申请 → 加入
        String phone = "13900000098";
        register(phone);
        String tokenC = farmer(phone);
        long applyC = postReq("/api/financing/apply", tokenC, """
                {"amount":70000,"usage":"SEED","industry":"PLANTING",
                 "province":"河南省","city":"周口市","loanTerm":"M12",
                 "description":"第三户"}""").longField("id");
        joinGroup(groupId, applyC, tokenC);

        Resp detail = getReq("/api/financing/group/" + groupId, farmer(FARMER_FRESH));
        assertEquals(3, detail.num("currentMembers"));
        assertEquals(1, detail.num("status"), "3 人应自动成型为「已匹配」");
        assertEquals(0, detail.num("remainingToForm"));

        // ⚠️ 不用 GET /api/financing/{id} 断言申请状态：详情里嵌了 group 对象，
        // 而 group 也有 status 字段；响应字段按字母序输出，group 排在 status 前面，
        // "取第一个 status"会拿到小组的状态。用列表的 status 筛选来验证，
        // 既无歧义，顺带也验了筛选功能。
        Resp pendingBank = getReq("/api/financing/list?status=2", tokenA);
        assertEquals(0, pendingBank.code());
        assertTrue(pendingBank.body.contains("\"id\":" + applyA + ","),
                "组内申请应被联动推进为「待银行审批」，但不在 status=2 列表里：" + pendingBank.body);

        // 成型后不再出现在"可加入"列表里
        Resp avail = getReq("/api/financing/groups?province=河南省", tokenC);
        assertTrue(!avail.body.contains("\"id\":" + groupId + ","),
                "成型小组不该可加入：" + avail.body);
    }

    @Test
    @DisplayName("M4：取消「待匹配」的申请 → 状态变 5，并回滚小组人数与总额")
    void cancelApplyRollsBackGroup() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        String tokenA = farmer(FARMER_APPLIED);
        long applyA = firstApplyIdOf(tokenA);
        joinGroup(groupId, applyA, tokenA);
        assertEquals(2, getReq("/api/financing/group/" + groupId, tokenA).num("currentMembers"));

        Resp cancel = deleteReq("/api/financing/apply/" + applyA, tokenA);
        assertEquals(0, cancel.code(), cancel.body);
        assertEquals(5, getReq("/api/financing/" + applyA, tokenA).num("status"));

        Resp detail = getReq("/api/financing/group/" + groupId, tokenA);
        assertEquals(1, detail.num("currentMembers"), "退出后人数应回滚为 1");
        // 剩下的是发起人自己那条 50000 的申请（newGroupWithCreator 里提交的），
        // 所以总额应该是 50000，不是 0 —— 退出只影响退出者那一份
        assertEquals(50000.0, detail.num("totalAmount"), 0.01,
                "退出后总额应只剩留下来那位成员的申请金额");
    }

    // ==================================================================================
    // 四、M6 银行端：列表 / 统计 / 审批
    // ==================================================================================

    @Test
    @DisplayName("M6：银行端列表默认只返回已成型小组，且带成员姓名")
    void bankGroupListOnlyFormed() throws Exception {
        Resp r = getReq("/api/bank/group/list", bank());
        assertEquals(0, r.code());
        // 种子里 LB-DEMO-0001 是已成型（status=1）的，必须出现
        assertTrue(r.body.contains("LB-DEMO-0001"), r.body);
        assertTrue(r.body.contains("\"username\""), "成员姓名应已批量回填：" + r.body);
        assertTrue(!r.body.contains("\"status\":0"), "默认不应返回招募中的小组：" + r.body);
    }

    @Test
    @DisplayName("M6：审批拒绝但不填理由 → 1001（跨字段条件必填）")
    void approveRejectWithoutRemarkFails() throws Exception {
        long groupId = demoGroupId();
        Resp r = putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"REJECTED\"}".formatted(groupId));
        assertEquals(400, r.status);
        assertEquals(1001, r.code());
    }

    @Test
    @DisplayName("M6：审批结果取值非法 → 1001")
    void approveInvalidResultFails() throws Exception {
        long groupId = demoGroupId();
        Resp r = putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"MAYBE\"}".formatted(groupId));
        assertEquals(400, r.status);
        assertEquals(1001, r.code());
    }

    @Test
    @DisplayName("M6：Mock 模式只传 groupId+result 即可审批；三项自动生成且都在契约范围内")
    void approveWithMockGeneratesValuesInRange() throws Exception {
        long groupId = newFormedGroup();
        Resp r = putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"APPROVED\"}".formatted(groupId));
        assertEquals(0, r.code(), r.body);

        Resp group = getReq("/api/bank/group/list?status=3", bank());
        assertTrue(group.body.contains("\"id\":" + groupId + ","),
                "审批后应进入 status=3 列表：" + group.body);

        // 审批明细挂在「申请详情」里（BankApprovalVO），列表里没有这三项，
        // 所以要先从列表拿到申请 id，再查详情
        long applyId = getReq("/api/financing/list?status=3", farmer(FARMER_FRESH)).longField("id");
        assertTrue(applyId > 0, "审批通过后应有 status=3 的申请");
        Resp detail = getReq("/api/financing/" + applyId, farmer(FARMER_FRESH));
        assertTrue(detail.body.contains("\"result\":\"APPROVED\""), detail.body);

        double groupTotal = groupTotal(groupId);
        int score = detail.intField("creditScore");
        double rate = detail.num("interestRate");
        double amount = detail.num("decisionAmount");
        assertTrue(score >= 0 && score <= 100, "信用分越界：" + score);
        assertTrue(rate >= 3.85 && rate <= 7.00, "利率越界：" + rate);
        assertTrue(amount >= groupTotal * 0.80 - 0.01 && amount <= groupTotal * 1.00 + 0.01,
                "核定额度应在总额 80%~100%：amount=" + amount + " total=" + groupTotal);
    }

    @Test
    @DisplayName("M6：重复审批同一小组 → 3006（审批结果不可撤回）")
    void duplicateApproveReturns3006() throws Exception {
        long groupId = newFormedGroup();
        assertEquals(0, putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"APPROVED\"}".formatted(groupId)).code());

        Resp again = putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"REJECTED\",\"remark\":\"改主意了\"}".formatted(groupId));
        assertEquals(400, again.status);
        assertEquals(3006, again.code());
    }

    @Test
    @DisplayName("M6：审批还没成型（人数<3）的小组 → 1001")
    void approveUnformedGroupFails() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);  // 只有 1 人
        Resp r = putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"APPROVED\"}".formatted(groupId));
        assertEquals(400, r.status);
        assertEquals(1001, r.code(), r.body);
    }

    @Test
    @DisplayName("M6：统计 —— 无审批时核定额度合计为 0 而不是 null")
    void statsReturnZeroNotNull() throws Exception {
        Resp r = getReq("/api/bank/stats", bank());
        assertEquals(0, r.code());
        assertTrue(r.body.contains("\"totalApprovedAmount\":0"),
                "无审批记录时合计应为 0：" + r.body);
    }

    @Test
    @DisplayName("M4+M6：已被银行审批的申请不能取消 → 3003（而不是笼统的 1001）")
    void cannotCancelApprovedApply() throws Exception {
        long groupId = newFormedGroup();
        assertEquals(0, putReq("/api/bank/approve", bank(),
                "{\"groupId\":%d,\"result\":\"APPROVED\"}".formatted(groupId)).code());

        long applyId = getReq("/api/financing/list?status=3", farmer(FARMER_FRESH)).longField("id");
        assertTrue(applyId > 0, "审批通过后应有 status=3 的申请");

        Resp r = deleteReq("/api/financing/apply/" + applyId, farmer(FARMER_FRESH));
        assertEquals(400, r.status);
        assertEquals(3003, r.code(),
                "应返回 3003（已被银行处理）—— 它比通用 1001 更能指引用户下一步怎么办");
    }

    // ==================================================================================
    // 五、M5 文章：发布 / 详情 / 点赞 / 评论
    // ==================================================================================

    @Test
    @DisplayName("M5：专家发布文章直接生效（status=1），游客可读")
    void expertArticlePublishesImmediately() throws Exception {
        long id = publishArticle(expert(), "planting", "专家文章标题");
        assertEquals(1, getReq("/api/article/" + id, expert()).intField("status"));
        assertEquals(200, getReq("/api/article/" + id, null).status);
    }

    @Test
    @DisplayName("M5：农户发布文章进入待审核（status=0）；游客读不到，作者与管理员能读到")
    void farmerArticleNeedsAudit() throws Exception {
        String token = farmer(FARMER_APPLIED);
        long id = publishArticle(token, "VEG", "农户文章标题");
        assertEquals(0, getReq("/api/article/" + id, token).intField("status"));

        Resp guest = getReq("/api/article/" + id, null);
        assertEquals(400, guest.status);
        assertEquals(4002, guest.code(), "未发布文章对游客应返回 4002");

        assertEquals(0, getReq("/api/article/" + id, token).code());
        assertEquals(0, getReq("/api/article/" + id, admin()).code());
    }

    @Test
    @DisplayName("M5：银行角色发布文章 → 403 + 1007")
    void bankCannotPublishArticle() throws Exception {
        Resp r = postReq("/api/article", bank(), """
                {"title":"银行发文","category":"planting","content":"内容"}""");
        assertEquals(403, r.status);
        assertEquals(1007, r.code());
    }

    @Test
    @DisplayName("M5：分类既不在契约枚举也不在字典 → 1001")
    void invalidArticleCategoryFails() throws Exception {
        Resp r = postReq("/api/article", expert(), """
                {"title":"乱填分类","category":"NO_SUCH_CATEGORY","content":"内容"}""");
        assertEquals(400, r.status);
        assertEquals(1001, r.code());
    }

    @Test
    @DisplayName("M5：详情返回 tags 数组（去重）、summary、authorName，且浏览量自增")
    void articleDetailFields() throws Exception {
        long id = publishArticle(expert(), "planting", "标签测试");
        Resp first = getReq("/api/article/" + id, null);
        assertEquals(0, first.code());
        assertTrue(first.body.contains("\"tags\":["), first.body);
        assertTrue(first.body.contains("\"summary\""), first.body);
        assertTrue(first.body.contains("\"authorName\""), first.body);
        assertTrue(first.body.contains("\"isLiked\":0"), "游客 isLiked 应为 0：" + first.body);

        Resp second = getReq("/api/article/" + id, null);
        assertTrue(second.intField("viewCount") > first.intField("viewCount"),
                "第二次访问浏览量应增加");
    }

    @Test
    @DisplayName("M5：点赞可切换 —— 再点一次取消，计数同步回退")
    void likeIsToggleable() throws Exception {
        long id = publishArticle(expert(), "planting", "点赞测试");
        String token = farmer(FARMER_APPLIED);

        Resp on = putReq("/api/article/" + id + "/like", token, "");
        assertEquals(0, on.code());
        assertEquals(1, on.intField("isLiked"));
        int afterLike = on.intField("likeCount");

        Resp off = putReq("/api/article/" + id + "/like", token, "");
        assertEquals(0, off.code());
        assertEquals(0, off.intField("isLiked"));
        assertEquals(afterLike - 1, off.intField("likeCount"));
    }

    @Test
    @DisplayName("M5：评论成功并对 commentCount 累加；超过 200 字 → 1001")
    void commentWorksAndLengthValidated() throws Exception {
        long id = publishArticle(expert(), "planting", "评论测试");
        String token = farmer(FARMER_APPLIED);

        Resp ok = postReq("/api/article/" + id + "/comment", token,
                "{\"content\":\"请问越冬期夜温控制在多少合适？\"}");
        assertEquals(0, ok.code(), ok.body);

        Resp detail = getReq("/api/article/" + id, token);
        assertTrue(detail.body.contains("\"comments\":[{"), "应返回评论列表：" + detail.body);
        assertEquals(1, detail.intField("commentCount"));

        Resp tooLong = postReq("/api/article/" + id + "/comment", token,
                "{\"content\":\"%s\"}".formatted("x".repeat(201)));
        assertEquals(400, tooLong.status);
        assertEquals(1001, tooLong.code());
    }

    @Test
    @DisplayName("M5：给未发布的文章评论 → 4002")
    void cannotCommentUnpublishedArticle() throws Exception {
        long id = publishArticle(farmer(FARMER_APPLIED), "VEG", "待审文章评论");
        Resp r = postReq("/api/article/" + id + "/comment", farmer(FARMER_APPLIED),
                "{\"content\":\"给待审核文章留言\"}");
        assertEquals(400, r.status);
        assertEquals(4002, r.code());
    }

    @Test
    @DisplayName("P1 回归：文章列表与审核仍可用（P3 改了 ArticleController 但没动这两个方法）")
    void p1ArticleListAndAuditStillWork() throws Exception {
        assertEquals(0, getReq("/api/article/list?status=0", admin()).code());

        long id = publishArticle(farmer(FARMER_APPLIED), "VEG", "待审核流转");
        Resp audit = putReq("/api/article/" + id + "/audit", admin(),
                "{\"status\":\"APPROVED\"}");
        assertEquals(0, audit.code(), audit.body);
        assertEquals(1, getReq("/api/article/" + id, null).intField("status"));
    }

    // ==================================================================================
    // 六、M5 专家列表与详情
    // ==================================================================================

    @Test
    @DisplayName("M5：专家列表只含已审核专家，含 specialty/avatar，且未审核的不出现")
    void expertListOnlyApproved() throws Exception {
        Resp r = getReq("/api/expert/list", null);
        assertEquals(0, r.code());
        assertTrue(r.body.contains("\"specialty\""), r.body);
        assertTrue(r.body.contains("\"avatar\""), r.body);
        assertTrue(r.body.contains("\"realName\":\"赵教授\""), r.body);
        // P1 的待审专家（status=0，t_expert 里没有行）不该出现
        assertTrue(!r.body.contains("张农艺师"),
                "待审核专家不该出现在列表：" + r.body);
    }

    @Test
    @DisplayName("M5：按专业领域模糊筛选生效")
    void expertListFiltersBySpecialty() throws Exception {
        Resp r = getReq("/api/expert/list?specialty=电商", null);
        assertEquals(0, r.code());
        assertEquals(1L, r.longField("total"));
        assertTrue(r.body.contains("周文博"), r.body);
    }

    @Test
    @DisplayName("M5：专家详情含 intro 与 recentArticles；不存在的专家 → 404 + 1008")
    void expertDetailFields() throws Exception {
        Resp r = getReq("/api/expert/" + EXPERT_ID, null);
        assertEquals(0, r.code());
        assertTrue(r.body.contains("\"intro\""), r.body);
        assertTrue(r.body.contains("\"recentArticles\""), r.body);

        Resp missing = getReq("/api/expert/99999", null);
        assertEquals(404, missing.status);
        assertEquals(1008, missing.code());
    }

    // ==================================================================================
    // 七、M5 在线提问
    // ==================================================================================

    @Test
    @DisplayName("M5：定向提问成功；农户列表只看到自己提的，expertName 已回填")
    void askTargetedQuestion() throws Exception {
        long qid = askQuestion(farmer(FARMER_APPLIED), EXPERT_ID, "定向提问标题");
        assertTrue(qid > 0);

        Resp mine = getReq("/api/question/list", farmer(FARMER_APPLIED));
        assertEquals(0, mine.code());
        assertTrue(mine.body.contains("\"expertName\":\"赵教授\""), mine.body);
    }

    @Test
    @DisplayName("M5：公开提问（expertId 省略）成功")
    void askPublicQuestion() throws Exception {
        Resp r = postReq("/api/question", farmer(FARMER_APPLIED), """
                {"title":"公开提问","content":"面向所有专家的问题"}""");
        assertEquals(0, r.code(), r.body);
    }

    @Test
    @DisplayName("M5：向不存在的专家提问 → 404 + 1008")
    void askUnknownExpertReturns1008() throws Exception {
        Resp r = postReq("/api/question", farmer(FARMER_APPLIED), """
                {"expertId":99999,"title":"无效专家","content":"内容"}""");
        assertEquals(404, r.status);
        assertEquals(1008, r.code());
    }

    @Test
    @DisplayName("M5：专家向自己提问 → 4006（脏数据拦截）")
    void expertCannotAskSelf() throws Exception {
        Resp r = postReq("/api/question", expert(),
                "{\"expertId\":%d,\"title\":\"自问自答\",\"content\":\"内容\"}".formatted(EXPERT_ID));
        assertEquals(400, r.status);
        assertEquals(4006, r.code());
    }

    @Test
    @DisplayName("M5：非专家回答 → 4001；重复回答 → 4003；回答后 answerCount 累加")
    void answerQuestionRules() throws Exception {
        long qid = askQuestion(farmer(FARMER_APPLIED), EXPERT_ID, "待专家回答");

        Resp byFarmer = putReq("/api/question/" + qid + "/answer", farmer(FARMER_APPLIED),
                "{\"answer\":\"农户不该能回答\"}");
        assertEquals(400, byFarmer.status);
        assertEquals(4001, byFarmer.code());

        Resp byExpert = putReq("/api/question/" + qid + "/answer", expert(),
                "{\"answer\":\"建议加二层膜，把夜温提到 15 度以上。\"}");
        assertEquals(0, byExpert.code(), byExpert.body);

        Resp again = putReq("/api/question/" + qid + "/answer", expert(),
                "{\"answer\":\"再答一次\"}");
        assertEquals(400, again.status);
        assertEquals(4003, again.code());

        assertEquals(1, getReq("/api/expert/" + EXPERT_ID, null).intField("answerCount"),
                "回答后专家答案数应累加");
    }

    @Test
    @DisplayName("M5：专家视角能看到「点名问我的 + 面向所有专家的公开提问」")
    void expertSeesOwnTodos() throws Exception {
        long targeted = askQuestion(farmer(FARMER_APPLIED), EXPERT_ID, "点名问赵教授");
        long publicQ = postReq("/api/question", farmer(FARMER_APPLIED), """
                {"title":"公开问题","content":"任何人都能答"}""").longField("id");

        Resp r = getReq("/api/question/list", expert());
        assertEquals(0, r.code());
        assertTrue(r.body.contains("\"id\":" + targeted + ","), r.body);
        assertTrue(r.body.contains("\"id\":" + publicQ + ","), r.body);
    }

    // ==================================================================================
    // 八、M5 专家预约
    // ==================================================================================

    @Test
    @DisplayName("M5：线上预约缺 meetingLink / 线下缺 meetingPlace → 1001")
    void appointmentCrossFieldValidation() throws Exception {
        String time = future(3);
        Resp online = postReq("/api/appointment", farmer(FARMER_APPLIED), """
                {"expertId":%d,"type":"online","appointmentTime":"%s",
                 "phone":"13900000013","description":"缺会议链接"}""".formatted(EXPERT_ID, time));
        assertEquals(400, online.status);
        assertEquals(1001, online.code());

        Resp offline = postReq("/api/appointment", farmer(FARMER_APPLIED), """
                {"expertId":%d,"type":"offline","appointmentTime":"%s",
                 "phone":"13900000013","description":"缺见面地点"}""".formatted(EXPERT_ID, time));
        assertEquals(400, offline.status);
        assertEquals(1001, offline.code());
    }

    @Test
    @DisplayName("M5：预约过去的时间 → 4004；时间格式非法 → 1001（不是 500）")
    void appointmentTimeValidation() throws Exception {
        Resp past = postReq("/api/appointment", farmer(FARMER_APPLIED), """
                {"expertId":%d,"type":"online","appointmentTime":"2020-01-01 10:00:00",
                 "phone":"13900000013","description":"过去时间",
                 "meetingLink":"https://meeting.example.com/x"}""".formatted(EXPERT_ID));
        assertEquals(400, past.status);
        assertEquals(4004, past.code());

        Resp bad = postReq("/api/appointment", farmer(FARMER_APPLIED), """
                {"expertId":%d,"type":"online","appointmentTime":"不是时间",
                 "phone":"13900000013","description":"格式错误",
                 "meetingLink":"https://meeting.example.com/x"}""".formatted(EXPERT_ID));
        assertEquals(400, bad.status);
        assertEquals(1001, bad.code());
    }

    @Test
    @DisplayName("M5：专家预约自己 → 4006")
    void expertCannotBookSelf() throws Exception {
        Resp r = postReq("/api/appointment", expert(), """
                {"expertId":%d,"type":"online","appointmentTime":"%s",
                 "phone":"13900000006","description":"预约自己",
                 "meetingLink":"https://meeting.example.com/x"}""".formatted(EXPERT_ID, future(3)));
        assertEquals(400, r.status);
        assertEquals(4006, r.code());
    }

    @Test
    @DisplayName("M5：预约成功 → 农户侧看到自己的（含 expertName）；专家侧看到约给自己的")
    void appointmentVisibilityByRole() throws Exception {
        long id = createAppointment(farmer(FARMER_APPLIED));
        assertTrue(id > 0);

        Resp mine = getReq("/api/appointment/list", farmer(FARMER_APPLIED));
        assertEquals(0, mine.code());
        assertTrue(mine.body.contains("\"expertName\":\"赵教授\""), mine.body);
        assertTrue(mine.body.contains("\"statusName\""), mine.body);

        Resp experts = getReq("/api/appointment/list", expert());
        assertEquals(0, experts.code());
        assertTrue(experts.body.contains("\"id\":" + id + ","), experts.body);
    }

    @Test
    @DisplayName("M5：非该专家处理预约 → 4001；处理成功后重复处理 → 4005")
    void handleAppointmentRules() throws Exception {
        long id = createAppointment(farmer(FARMER_APPLIED));

        Resp byFarmer = putReq("/api/appointment/" + id + "/handle", farmer(FARMER_APPLIED),
                "{\"status\":\"ACCEPTED\"}");
        assertEquals(400, byFarmer.status);
        assertEquals(4001, byFarmer.code());

        Resp badStatus = putReq("/api/appointment/" + id + "/handle", expert(),
                "{\"status\":\"MAYBE\"}");
        assertEquals(400, badStatus.status);
        assertEquals(1001, badStatus.code());

        Resp ok = putReq("/api/appointment/" + id + "/handle", expert(),
                "{\"status\":\"ACCEPTED\"}");
        assertEquals(0, ok.code(), ok.body);

        Resp again = putReq("/api/appointment/" + id + "/handle", expert(),
                "{\"status\":\"REJECTED\"}");
        assertEquals(400, again.status);
        assertEquals(4005, again.code());
    }

    @Test
    @DisplayName("M5：管理员越权处理别人的预约 → 被拦（1007 或 4001）")
    void adminCannotHandleOthersAppointment() throws Exception {
        long id = createAppointment(farmer(FARMER_APPLIED));
        Resp r = putReq("/api/appointment/" + id + "/handle", admin(),
                "{\"status\":\"ACCEPTED\"}");
        assertTrue(r.status == 403 || r.status == 400, "应被拦下：" + r.status);
        assertTrue(r.code() == 1007 || r.code() == 4001, "错误码：" + r.code());
    }

    // ==================================================================================
    // 九、地基回归（确认 P3 的改动没有破坏 P1 / P2 的能力）
    // ==================================================================================

    @Test
    @DisplayName("回归：P1 字典查询对所有登录用户开放；用户信息接口正常；P2 首页正常")
    void baselineStillWorks() throws Exception {
        assertEquals(0, getReq("/api/admin/dict/industry", farmer(FARMER_APPLIED)).code());
        assertEquals(0, getReq("/api/user/info", farmer(FARMER_APPLIED)).code());
        assertEquals(0, getReq("/api/ping", null).code());
        // P2 的 M2 首页（游客可访问的接口之一）
        assertEquals(200, getReq("/api/home/banners", null).status);
    }

    // ==================================================================================
    // 测试辅助：把 MockMvc 的样板收在一处，让用例读起来像业务流程
    // ==================================================================================

    /** 响应 + 状态码的小包装。用 record 而不是 Map，取值时不用强转 */
    private record Resp(int status, String body) {
        /** 业务错误码。非 JSON 响应返回 -1，方便断言失败时看出是"格式不对"还是"码不对" */
        int code() {
            Matcher m = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)").matcher(body);
            return m.find() ? Integer.parseInt(m.group(1)) : -1;
        }

        /** 取第一个匹配的整数字段 */
        int intField(String field) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(body);
            return m.find() ? Integer.parseInt(m.group(1)) : -1;
        }

        /** 取第一个匹配的数字字段（含小数） */
        double num(String field) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?[0-9.]+)").matcher(body);
            return m.find() ? Double.parseDouble(m.group(1)) : -1;
        }

        /**
         * 取第一个匹配的整数字段并返回 {@code long}。
         *
         * <p>为什么单独有一个方法？因为 id 这类值必须用 long 接 ——
         * Java 不允许把 {@code double} 隐式窄化回 {@code long}，
         * 用 {@link #num} 接 id 会直接编译不过（"从 double 转换到 long 可能会有损失"）。
         * 按用途分成两个方法读起来也更明确：{@code longField} 是"这是标识/计数"，
         * {@code num} 是"这是金额/利率"。
         */
        long longField(String field) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(body);
            return m.find() ? Long.parseLong(m.group(1)) : -1L;
        }

        /** 取第一个匹配的字符串字段 */
        String str(String field) {
            Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            return m.find() ? m.group(1) : null;
        }
    }

    private Resp call(String method, String path, String token, String jsonBody) throws Exception {
        var builder = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        if (jsonBody != null && !jsonBody.isEmpty()) {
            builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString();
        assertNotNull(body, "响应体为空：" + method + " " + path);
        return new Resp(result.getResponse().getStatus(), body);
    }

    private Resp getReq(String path, String token) throws Exception {
        return call("GET", path, token, null);
    }

    private Resp postReq(String path, String token, String body) throws Exception {
        return call("POST", path, token, body);
    }

    private Resp putReq(String path, String token, String body) throws Exception {
        return call("PUT", path, token, body);
    }

    private Resp deleteReq(String path, String token) throws Exception {
        return call("DELETE", path, token, null);
    }

    // ---------------- 登录 ----------------

    private String login(String phone) throws Exception {
        Resp r = postReq("/api/user/login", null,
                "{\"phone\":\"%s\",\"password\":\"%s\"}".formatted(phone, PASSWORD));
        assertEquals(0, r.code(), "登录失败：" + phone + " -> " + r.body);
        String token = r.str("token");
        assertNotNull(token, "登录未返回 token：" + r.body);
        return token;
    }

    private String tokenOf(String phone) throws Exception {
        String cached = tokenCache.get(phone);
        if (cached != null) {
            return cached;
        }
        String token = login(phone);
        tokenCache.put(phone, token);
        return token;
    }

    private String farmer(String phone) throws Exception {
        return tokenOf(phone);
    }

    private String bank() throws Exception {
        return tokenOf(BANK_PHONE);
    }

    private String expert() throws Exception {
        return tokenOf(EXPERT_PHONE);
    }

    private String admin() throws Exception {
        return tokenOf(ADMIN_PHONE);
    }

    private void register(String phone) throws Exception {
        Resp r = postReq("/api/user/register", null, """
                {"phone":"%s","password":"%s","confirmPassword":"%s",
                 "verifyCode":"111111","role":"farmer"}""".formatted(phone, PASSWORD, PASSWORD));
        assertEquals(0, r.code(), "注册失败：" + r.body);
    }

    // ---------------- 业务动作 ----------------

    /** 取某用户当前"待匹配"的申请 id（种子里给刘四海准备了一条） */
    private long firstApplyIdOf(String token) throws Exception {
        Resp r = getReq("/api/financing/list?status=0", token);
        assertEquals(0, r.code(), r.body);
        long id = r.longField("id");
        assertTrue(id > 0, "该用户没有「待匹配」的申请，用例前置条件不满足：" + r.body);
        return id;
    }

    /** 用指定农户"提交申请 + 发起小组"，返回新小组 id（小组只有发起人 1 人，金额 50000） */
    private long newGroupWithCreator(String creatorPhone) throws Exception {
        String token = farmer(creatorPhone);
        // 发起小组要求发起人有一条「待匹配」的申请；没有就先补一条。
        // 判定用"列表里第一条的 id 是否 > 0"，因为 Resp.longField 取不到字段时返回 -1
        if (getReq("/api/financing/list?status=0", token).longField("id") <= 0) {
            Resp apply = postReq("/api/financing/apply", token, """
                    {"amount":50000,"usage":"SEED","industry":"PLANTING",
                     "province":"河南省","city":"周口市","loanTerm":"M12",
                     "description":"用例前置：提交申请"}""");
            assertEquals(0, apply.code(), "前置申请失败：" + apply.body);
        }
        Resp r = postReq("/api/financing/create-group", token, """
                {"industry":"PLANTING","province":"河南省","city":"周口市"}""");
        assertEquals(0, r.code(), "发起小组失败：" + r.body);
        return r.longField("id");
    }

    private void joinGroup(long groupId, long applyId, String token) throws Exception {
        Resp r = postReq("/api/financing/join", token,
                "{\"groupId\":%d,\"applyId\":%d}".formatted(groupId, applyId));
        assertEquals(0, r.code(), "加入小组失败：" + r.body);
    }

    /** 造一个"已成型（3 人）"的小组，用于银行审批类用例 */
    private long newFormedGroup() throws Exception {
        long groupId = newGroupWithCreator(FARMER_FRESH);
        String tokenA = farmer(FARMER_APPLIED);
        joinGroup(groupId, firstApplyIdOf(tokenA), tokenA);

        String phone = "13900000097";
        register(phone);
        String tokenC = farmer(phone);
        long applyC = postReq("/api/financing/apply", tokenC, """
                {"amount":70000,"usage":"SEED","industry":"PLANTING",
                 "province":"河南省","city":"周口市","loanTerm":"M12",
                 "description":"凑第三个人"}""").longField("id");
        joinGroup(groupId, applyC, tokenC);
        return groupId;
    }

    /**
     * 种子里那个已成型的小组（LB-DEMO-0001）的 id。
     *
     * <p>⚠️ <b>不要用"id 在 groupNo 前面"的正则去匹配。</b>
     * 响应体的字段是按字母序输出的（{@code city, createdAt, creatorId, currentMembers,
     * groupNo, id, …}），所以 id 排在 groupNo <b>之后</b>。
     * 正确做法是绕开 JSON 结构：李农户的申请就在这个演示小组里，
     * 从他自己的申请列表里读 {@code groupId} 即可。
     */
    private long demoGroupId() throws Exception {
        Resp list = getReq("/api/financing/list", farmer(FARMER_IN_DEMO_GROUP));
        assertEquals(0, list.code(), list.body);
        long groupId = list.longField("groupId");
        assertTrue(groupId > 0, "李农户的申请应已挂在演示小组上：" + list.body);
        return groupId;
    }

    /**
     * 取某个小组的申请总额。
     *
     * <p>用"小组详情"这个<b>单对象</b>接口取值，而不是从银行端列表里解析 ——
     * 列表是数组，要正确定位到目标对象就得处理花括号嵌套，脆弱且没必要。
     * 小组详情对任何登录用户开放，所以用农户 Token 也能读。
     */
    private double groupTotal(long groupId) throws Exception {
        Resp r = getReq("/api/financing/group/" + groupId, farmer(FARMER_FRESH));
        assertEquals(0, r.code(), r.body);
        return r.num("totalAmount");
    }

    private long publishArticle(String token, String category, String title) throws Exception {
        Resp r = postReq("/api/article", token, """
                {"title":"%s","category":"%s","content":"这是一段足够长的正文内容，用于测试详情页的摘要截取逻辑。",
                 "tags":["测试","标签"]}""".formatted(title, category));
        assertEquals(0, r.code(), "发布文章失败：" + r.body);
        long id = r.longField("id");
        assertTrue(id > 0, r.body);
        return id;
    }

    private long askQuestion(String token, Long expertId, String title) throws Exception {
        String body = expertId == null
                ? "{\"title\":\"%s\",\"content\":\"问题详情内容\"}".formatted(title)
                : "{\"expertId\":%d,\"title\":\"%s\",\"content\":\"问题详情内容\"}"
                .formatted(expertId, title);
        Resp r = postReq("/api/question", token, body);
        assertEquals(0, r.code(), "提问失败：" + r.body);
        return r.longField("id");
    }

    private long createAppointment(String token) throws Exception {
        Resp r = postReq("/api/appointment", token, """
                {"expertId":%d,"type":"online","appointmentTime":"%s",
                 "phone":"13900000013","description":"想请教大棚保温改造方案",
                 "meetingLink":"https://meeting.example.com/rxt-test"}""".formatted(EXPERT_ID, future(3)));
        assertEquals(0, r.code(), "创建预约失败：" + r.body);
        return r.longField("id");
    }

    private String future(int days) {
        return LocalDateTime.now().plusDays(days)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
