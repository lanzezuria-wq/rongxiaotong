package com.rxt.finance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请加入联保小组入参 —— {@code POST /api/financing/join}（M4）。
 *
 * <p><b>为什么要同时传 groupId 和 applyId？</b>因为"加入小组"这个动作动的是两样东西：
 * 小组要加一个成员，某个融资申请要绑定到这个小组上。只传 groupId 的话，
 * 服务端得去猜"用这个人的哪一条申请"—— 而一个农户可能有多条申请
 * （历史已拒绝的 + 当前进行中的），猜错就绑错了。
 * 让前端明确指定，是把歧义消灭在接口层。
 *
 * <p>服务端仍会校验两件事（不能只信前端）：
 * <ul>
 *   <li>该 applyId 必须属于当前登录用户 —— 否则可以拿别人的申请去凑自己的小组；</li>
 *   <li>该 applyId 必须还没被别的小组占用。</li>
 * </ul>
 */
@Data
public class JoinGroupDTO {

    @NotNull(message = "联保小组 id 不能为空")
    private Long groupId;

    @NotNull(message = "融资申请 id 不能为空")
    private Long applyId;
}
