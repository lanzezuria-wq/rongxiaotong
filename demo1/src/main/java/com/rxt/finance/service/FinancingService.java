package com.rxt.finance.service;

import com.rxt.common.PageResult;
import com.rxt.finance.dto.CreateGroupDTO;
import com.rxt.finance.dto.FinancingApplyDTO;
import com.rxt.finance.dto.JoinGroupDTO;
import com.rxt.finance.vo.FinancingApplyDetailVO;
import com.rxt.finance.vo.FinancingApplyResultVO;
import com.rxt.finance.vo.FinancingApplyVO;
import com.rxt.finance.vo.GuaranteeGroupDetailVO;
import com.rxt.finance.vo.GuaranteeGroupVO;

import java.util.List;

/**
 * 融资服务 —— M4（8 个接口）。
 *
 * <p>这个模块的核心是<b>一条状态机</b>，不是 8 个独立的 CRUD：
 * <pre>
 *   申请(status=0 待匹配)
 *        │  createGroup(自动入组) 或 join
 *        ▼
 *   申请(status=1 已匹配)  ── 小组人数达 3 人 ──>  申请(status=2 待银行审批)
 *                                                    │
 *                                    银行通过 ──> 3 已通过
 *                                    银行拒绝 ──> 4 已拒绝
 *
 *   任意"进行中"(0/1/2) 且小组未被银行处理 ──申请人取消──> 5 已取消
 * </pre>
 * 所以"取消申请"会反向影响小组（人数 -1、可能退回招募中），
 * "加入小组"会正向推进小组（人数 +1、可能成型入池）。
 * 这两条反向/正向的联动<b>全部收在 {@code syncGroupState} 一个方法里</b>，
 * 不允许任何别的地方直接改小组的 current_members / status ——
 * 否则一定会出现"成员表 3 人、小组计数写 2"的不一致。
 */
public interface FinancingService {

    /**
     * 提交融资申请 —— {@code POST /api/financing/apply}。
     *
     * <p>规则：
     * <ol>
     *   <li>只有 {@code farmer} 角色能申请（银行/专家/管理员不行）；</li>
     *   <li>同一用户同时只能有<b>一条进行中</b>的申请（已有则报 3001）；</li>
     *   <li>金额 10000~500000，用途/行业/期限必须能在字典里查到；</li>
     *   <li>创建后立即按"同行业 + 同省"匹配可加入的小组，一起返回，
     *       前端就能直接提示"已为您匹配到 N 个小组"。</li>
     * </ol>
     *
     * @param userId 当前登录用户（从 Token 取，不接受前端传参）
     */
    FinancingApplyResultVO apply(Long userId, FinancingApplyDTO dto);

    /**
     * 我的融资申请列表 —— {@code GET /api/financing/list}。
     *
     * <p>只返回<b>当前用户自己的</b>申请。列表页不需要小组和审批详情，那两项走 {@link #detail}。
     */
    PageResult<FinancingApplyVO> myApplies(Long userId, Integer status, int page, int size);

    /**
     * 融资申请详情 —— {@code GET /api/financing/{id}}。
     *
     * <p><b>越权控制</b>：默认只有申请人本人能看自己的申请详情；
     * 但 {@code admin} 和 {@code bank} 也要看（银行审批时得知道这笔钱干什么用），
     * 所以放行这两个角色。
     */
    FinancingApplyDetailVO detail(Long userId, String role, Long applyId);

    /**
     * 取消申请 —— {@code DELETE /api/financing/apply/{id}}。
     *
     * <p>规则：只有进行中（0/1/2）的申请能取消；一旦小组被银行处理过（已通过/已拒绝）
     * 就不能取消，报 <b>3003</b>。
     *
     * <p>取消时如果已加入小组，会<b>把成员记录一起删掉并回滚小组的聚合数据</b> ——
     * 否则小组里会留一个"金额算进去但人已经退了"的幽灵成员。
     */
    void cancel(Long userId, Long applyId);

    /**
     * 可加入的联保小组列表 —— {@code GET /api/financing/groups}。
     *
     * <p>只返回<b>招募中（status=0）且未满员</b>的小组。已成型进入银行审批池的不在这里 ——
     * 往一个"材料已提交银行"的小组里加人，会让银行已核定的额度依据失效
     * （银行审的是"这 3 个人"的联保结构，凭空变成 4 个人就是另一笔业务了）。
     *
     * @param userId 用于排除"自己已经在里面的小组"，避免前端提示重复加入
     */
    List<GuaranteeGroupVO> availableGroups(Long userId, String industry, String province);

    /**
     * 联保小组详情 —— {@code GET /api/financing/group/{id}}。
     *
     * <p>任何登录用户都能看（农户要判断这个组值不值得加入；银行审批时也要看成员名单）。
     */
    GuaranteeGroupDetailVO groupDetail(Long groupId);

    /**
     * 申请加入联保小组 —— {@code POST /api/financing/join}。
     *
     * <p>规则：
     * <ol>
     *   <li>小组必须是"招募中"且未满员，否则报 3005 / 3002；</li>
     *   <li>申请必须属于当前用户且处于"待匹配"，否则报 3007；</li>
     *   <li>不能重复加入同一小组（唯一索引 + Service 双保险）。</li>
     * </ol>
     * 加入成功后如果小组首次达到 3 人（成型），会把组内所有申请推进到"待银行审批"。
     */
    void join(Long userId, JoinGroupDTO dto);

    /**
     * 发起新联保小组 —— {@code POST /api/financing/create-group}。
     *
     * <p><b>要求发起人先有"待匹配"的融资申请</b>，并把该申请自动入组。
     * 理由：小组在业务上就是"一组融资申请的集合"，
     * 允许创建空小组会产生"1 人小组挂着没人加入"的垃圾数据，
     * 而且发起人自己也进不去（他还没申请）。
     * 所以 {@link #join} 与 {@link #createGroup} 的入口条件保持一致：都要先有申请。
     *
     * @return 新建小组的 id
     */
    Long createGroup(Long userId, CreateGroupDTO dto);
}
