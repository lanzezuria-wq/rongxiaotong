package com.rxt.finance.controller;

import com.rxt.common.PageResult;
import com.rxt.common.Result;
import com.rxt.finance.dto.CreateGroupDTO;
import com.rxt.finance.dto.FinancingApplyDTO;
import com.rxt.finance.dto.JoinGroupDTO;
import com.rxt.finance.service.FinancingService;
import com.rxt.finance.vo.FinancingApplyDetailVO;
import com.rxt.finance.vo.FinancingApplyResultVO;
import com.rxt.finance.vo.FinancingApplyVO;
import com.rxt.finance.vo.GuaranteeGroupDetailVO;
import com.rxt.finance.vo.GuaranteeGroupVO;
import com.rxt.security.LoginUser;
import com.rxt.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 融资服务接口 —— M4（8 个接口）。
 *
 * <p><b>权限说明</b>：这 8 个接口在 {@code SecurityConfig} 里都只要求"已登录"，
 * 没有按 URL 配角色限制。原因是角色要求是<b>"按动作"而不是"按路径"</b>的：
 * <ul>
 *   <li>{@code POST /financing/apply}、{@code create-group} —— 只有农户（Service 里校验）；</li>
 *   <li>{@code GET /financing/{id}} —— 本人，但 admin/bank 也要看（审批需要，Service 里放行）；</li>
 *   <li>{@code GET /financing/groups}、{@code group/{id}} —— 任何登录用户都能看。</li>
 * </ul>
 * 如果硬要在 URL 规则里表达，就得为 {@code /financing/**} 写出四条互相穿插的规则，
 * 既难读又容易在调整时出漏洞。业务规则统一收在 Service 里更清楚。
 *
 * <p><b>另外注意"越权"不靠 URL 参数</b>：所有接口都不接收 userId 参数，
 * 身份一律从 Token 里取（{@link SecurityUtils#currentUserId()}）。
 * 这样"用别人的 id 查别人的申请"这类攻击面从接口形状上就不存在。
 */
@Tag(name = "11. M4 融资服务", description = "融资申请 + 联保小组（8 个接口）")
@RestController
@RequestMapping("/api/financing")
@RequiredArgsConstructor
public class FinancingController {

    private final FinancingService financingService;

    @Operation(summary = "提交融资申请",
            description = "仅农户（含买家，两者共用 farmer 角色）。"
                    + "同一用户同时只能有一条进行中的申请，重复提交返回 3001。"
                    + "金额范围 10000~500000。"
                    + "响应里会带 matchedGroups —— 已按「同行业 + 同省」帮您匹配好可加入的小组，"
                    + "前端可直接引导『立即加入』。")
    @PostMapping("/apply")
    public Result<FinancingApplyResultVO> apply(@Valid @RequestBody FinancingApplyDTO dto) {
        FinancingApplyResultVO vo = financingService.apply(SecurityUtils.currentUserId(), dto);
        int matched = vo.getMatchedGroups() == null ? 0 : vo.getMatchedGroups().size();
        return Result.ok(matched > 0
                ? "申请提交成功，为您匹配到 " + matched + " 个联保小组"
                : "申请提交成功，暂无匹配的联保小组，您可以自己发起一个", vo);
    }

    @Operation(summary = "我的融资申请列表",
            description = "只返回当前登录用户自己的申请。status 可选（0 待匹配 / 1 已匹配 / "
                    + "2 待银行审批 / 3 已通过 / 4 已拒绝 / 5 已取消）。")
    @GetMapping("/list")
    public Result<PageResult<FinancingApplyVO>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Integer status) {
        return Result.ok(financingService.myApplies(SecurityUtils.currentUserId(), status, page, size));
    }

    @Operation(summary = "融资申请详情",
            description = "比列表多三项：用途说明、所属联保小组摘要、银行审批结果。"
                    + "本人可看自己的；admin 与 bank 因审批需要也可查看。"
                    + "⚠️ 响应里嵌了一个 group 对象，它也有 status 字段 —— "
                    + "前端取值时要看清是申请的状态还是小组的状态（字段名相同）。")
    @GetMapping("/{id}")
    public Result<FinancingApplyDetailVO> detail(@PathVariable Long id) {
        LoginUser loginUser = SecurityUtils.currentUser();
        return Result.ok(financingService.detail(loginUser.getUserId(), loginUser.getRole(), id));
    }

    @Operation(summary = "取消融资申请",
            description = "只有进行中（待匹配/已匹配/待银行审批）的申请能取消。"
                    + "若所在小组已被银行审批（通过或拒绝），返回 3003 —— 审批结果不可撤回。"
                    + "取消时会一并退出联保小组，并回滚小组的人数与总额。")
    @DeleteMapping("/apply/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        financingService.cancel(SecurityUtils.currentUserId(), id);
        return Result.ok("申请已取消，已退出所在联保小组", null);
    }

    @Operation(summary = "可加入的联保小组列表",
            description = "只返回「招募中且未满员」的小组，并排除自己已经在里面的。"
                    + "已成型进入银行审批池的小组不在列表里（往里面加人会让银行已核定的额度依据失效）。"
                    + "industry / province 传了就按它们强匹配。最多返回最近 100 个。")
    @GetMapping("/groups")
    public Result<List<GuaranteeGroupVO>> groups(@RequestParam(required = false) String industry,
                                                @RequestParam(required = false) String province) {
        return Result.ok(financingService.availableGroups(
                SecurityUtils.currentUserId(), industry, province));
    }

    @Operation(summary = "联保小组详情",
            description = "任何登录用户可看（农户判断是否加入、银行审批时核验成员）。含成员名单。")
    @GetMapping("/group/{id}")
    public Result<GuaranteeGroupDetailVO> groupDetail(@PathVariable Long id) {
        return Result.ok(financingService.groupDetail(id));
    }

    @Operation(summary = "申请加入联保小组",
            description = "需要同时传 groupId 和 applyId（服务端会校验这条申请属于你、且尚未被别的小组占用）。"
                    + "小组必须处于「招募中」且未满员，否则返回 3005 / 3002。"
                    + "加入后若小组首次达到 3 人成型，组内所有申请会自动推进为「待银行审批」。")
    @PostMapping("/join")
    public Result<Void> join(@Valid @RequestBody JoinGroupDTO dto) {
        financingService.join(SecurityUtils.currentUserId(), dto);
        return Result.ok("已加入联保小组", null);
    }

    @Operation(summary = "发起新联保小组",
            description = "仅农户。发起人必须已有一条「待匹配」的融资申请，该申请会自动成为小组的第一个成员"
                    + "（小组在业务上就是一组融资申请的集合，所以不允许创建空小组）。"
                    + "成员达到 3 人即为成型，之后进入银行审批池。")
    @PostMapping("/create-group")
    public Result<Map<String, Long>> createGroup(@Valid @RequestBody CreateGroupDTO dto) {
        Long id = financingService.createGroup(SecurityUtils.currentUserId(), dto);
        // 契约里这个接口的 data 是 {id}，所以包成单键 Map 而不是直接返回数字 ——
        // 直接返回 123 也是合法 JSON，但前端就没法用 data.id 取值了
        return Result.ok("联保小组创建成功", Map.of("id", id));
    }
}
