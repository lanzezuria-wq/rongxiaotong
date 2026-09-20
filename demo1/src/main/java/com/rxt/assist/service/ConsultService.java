package com.rxt.assist.service;

import com.rxt.assist.dto.AnswerDTO;
import com.rxt.assist.dto.AppointmentDTO;
import com.rxt.assist.dto.AppointmentHandleDTO;
import com.rxt.assist.dto.QuestionDTO;
import com.rxt.assist.vo.AppointmentVO;
import com.rxt.assist.vo.QuestionVO;

import java.util.List;

/**
 * 专家咨询服务 —— M5 专家助力（在线提问 + 专家预约，6 个接口）。
 *
 * <p>提问和预约放在一个服务里，是因为它们<b>共享同一条权限模型</b>：
 * 两者都是"农户发起、专家处理"，都要判断"当前用户是不是（哪个）专家"，
 * 都要区分"我发起的"和"发给我的"两个视角。拆成两个 Service 会让这套判断写两遍。
 *
 * <p><b>两个视角是这个模块最需要解释的地方</b>，因为契约里
 * {@code GET /api/question/list} 和 {@code GET /api/appointment/list}
 * 的说明都写的是"我的"：
 * <pre>
 *   普通用户（farmer）看 → 我发起的那些
 *   专家（expert）看     → 别人发给我的那些（这是专家的"待办"）
 * </pre>
 * 如果两个角色看到的是同一份数据，专家就永远找不到自己该处理的请求 ——
 * 契约里没有单独的"专家待办列表"接口，所以只能让同一个接口按角色返回不同数据。
 * <b>这一处需要在联调时和 P4 明确</b>，否则前端会把专家页的数据源接错。
 */
public interface ConsultService {

    /**
     * 提问 —— {@code POST /api/question}。
     *
     * <p>{@code expertId} 可空：为空时问题进入公共池，任何已通过审核的专家都能回答。
     * 填了的话，服务端会校验这个 id 确实是已通过的专家（否则问题会永远没人答），
     * 并且不允许向自己提问。
     *
     * @return 新问题 id
     */
    Long ask(Long userId, QuestionDTO dto);

    /**
     * 提问列表 —— {@code GET /api/question/list}。
     *
     * <p>按角色分视角，详见类注释。
     *
     * @param status 0 待回答 / 1 已回答。可空
     */
    List<QuestionVO> myQuestions(Long userId, String role, Integer status);

    /**
     * 专家回答 —— {@code PUT /api/question/{id}/answer}。
     *
     * <p>规则：
     * <ol>
     *   <li>只能回答一次 —— 已回答的问题再答报 4003（与审核"结果不可撤回"同一原则）；</li>
     *   <li>定向提问（expertId 不为空）只有被指定的专家能答，其它专家报 1007；</li>
     *   <li>公开提问（expertId 为空）任何已通过审核的专家都能答，先到先得；</li>
     *   <li>不能回答自己的提问；</li>
     *   <li>回答成功后给该专家的 {@code answer_count} 累加 —— 这是农户判断
     *       "谁更活跃"的依据，在专家列表页直接展示。</li>
     * </ol>
     */
    void answer(Long userId, Long questionId, AnswerDTO dto);

    /**
     * 发起预约 —— {@code POST /api/appointment}。
     *
     * <p>规则：专家必须存在且已通过审核；不能预约自己；预约时间必须晚于当前时间；
     * {@code online} 必填会议链接、{@code offline} 必填见面地点
     * （跨字段条件必填，所以写在 Service 里）。
     *
     * @return 新预约 id
     */
    Long createAppointment(Long userId, AppointmentDTO dto);

    /**
     * 预约列表 —— {@code GET /api/appointment/list}。
     *
     * <p>按角色分视角，同 {@link #myQuestions}。
     */
    List<AppointmentVO> myAppointments(Long userId, String role);

    /**
     * 专家处理预约 —— {@code PUT /api/appointment/{id}/handle}。
     *
     * <p>规则：只有被预约的那位专家能处理；只能处理 {@code PENDING} 的预约
     * （已处理的报 4005，结果不可撤回）；状态只能是 {@code ACCEPTED} 或 {@code REJECTED}。
     */
    void handleAppointment(Long userId, Long appointmentId, AppointmentHandleDTO dto);
}
