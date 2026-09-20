package com.rxt.finance.service;

import com.rxt.finance.dto.BankApproveDTO;
import com.rxt.finance.vo.BankGroupVO;
import com.rxt.finance.vo.BankStatsVO;

import java.util.List;

/**
 * 融资管理服务（银行端）—— M6（3 个接口）。
 *
 * <p>银行审的对象是<b>联保小组</b>而不是单个农户，所以这个模块的全部操作都围绕
 * {@code groupId} 展开：看有哪些已成型待审批的小组 → 一次审批决定整个小组的命运 →
 * 统计审批员的"工作看板"。
 *
 * <p><b>权限是三层冗余的</b>（都在 {@code BankServiceImpl} / {@code BankController} /
 * {@code SecurityConfig} 里落地），挡的是不同的事：
 * URL 规则挡住"别的角色点进来"，{@code @PreAuthorize} 挡住"有人把 URL 规则改松了"，
 * Service 里查库挡住"Token 里的角色是签发时的快照、而现在角色已被管理员改掉"。
 */
public interface BankService {

    /**
     * 银行端联保小组列表 —— {@code GET /api/bank/group/list}。
     *
     * <p><b>默认只返回"已成型"的小组</b>（status >= 1），不返回招募中的。
     * 因为一个 1 人小组对银行没有审批价值，混在列表里只会干扰审批员。
     *
     * @param status 可选。不传 → 返回已成型的所有小组；传了就精确筛选
     *               （1 已匹配 / 2 待审批 / 3 已通过 / 4 已拒绝）
     */
    List<BankGroupVO> groupList(Integer status);

    /**
     * 审批联保小组 —— {@code PUT /api/bank/approve}。
     *
     * <p>规则：
     * <ol>
     *   <li>小组必须"已匹配"或"待银行审批"，否则报参数错误或 3006（已审批不可撤回）；</li>
     *   <li>拒绝时必须填理由（跨字段规则，Service 校验）；</li>
     *   <li>信用分/利率/核定额度：Mock 模式开启时可自动生成，关闭时必须由前端传全；</li>
     *   <li>审批结果写 {@code t_bank_approval}，同时把小组和组内所有申请的状态一起改掉 ——
     *       <b>三处状态必须同时变</b>，所以整个方法在一个事务里。</li>
     * </ol>
     *
     * @param bankUserId 当前登录的银行人员 id（作为 t_bank_approval.bank_id 留痕）
     */
    void approve(Long bankUserId, BankApproveDTO dto);

    /**
     * 银行端统计 —— {@code GET /api/bank/stats}。
     *
     * <p>实时算，不缓存。理由见 {@code BankStatsVO} 的类注释
     * （审批员看到的"待办数"过期会让他误判"审批没生效"）。
     */
    BankStatsVO stats();
}
