package com.rxt.assist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.assist.constant.AppointmentConst;
import com.rxt.assist.dto.AnswerDTO;
import com.rxt.assist.dto.AppointmentDTO;
import com.rxt.assist.dto.AppointmentHandleDTO;
import com.rxt.assist.dto.QuestionDTO;
import com.rxt.assist.entity.Appointment;
import com.rxt.assist.entity.Question;
import com.rxt.assist.mapper.AppointmentMapper;
import com.rxt.assist.mapper.QuestionMapper;
import com.rxt.assist.service.ConsultService;
import com.rxt.assist.service.ExpertService;
import com.rxt.assist.vo.AppointmentVO;
import com.rxt.assist.vo.QuestionVO;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.common.exception.ResourceNotFoundException;
import com.rxt.common.util.DateUtils;
import com.rxt.entity.Expert;
import com.rxt.entity.User;
import com.rxt.mapper.ExpertMapper;
import com.rxt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 专家咨询服务实现 —— M5（提问 + 预约）。
 *
 * <p>（"同一个列表接口按角色返回不同数据"的设计理由见 {@link ConsultService} 的类注释。）
 *
 * <p>这里集中说明三处实现取舍：
 *
 * <h3>一、为什么 {@code answer} 和 {@code handleAppointment} 都要先查一次库确认身份？</h3>
 * 因为 JWT 里的 role 只是"签发时是 expert"，但<b>判断能不能回答某个问题、
 * 能不能处理某个预约，靠的不是角色而是"你的 t_expert.id 是不是这条记录的 expertId"</b>。
 * 角色和具体记录的归属是两回事：A 专家不能替 B 专家处理预约，
 * 而两个人的 role 都是 expert。所以必须查库拿到自己的 t_expert.id 再比对。
 *
 * <h3>二、为什么回答成功后要更新 {@code t_expert.answer_count}？</h3>
 * 因为这个计数在专家列表页直接展示（"已回答 12 个问题"），
 * 是农户判断专家活跃度的主要依据。用 SQL 表达式自增而不是"查出来 +1 再写回"，
 * 避免并发下丢计数。
 *
 * <h3>三、预约时间为什么必须显式校验"晚于当前时间"？</h3>
 * 因为"预约一个已经过去的时间"在业务上是无意义的脏数据，
 * 而前端的时间选择器可以被绕过（直接构造请求）。
 * 这正是"前端校验是体验、后端校验是正确性"的典型例子。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultServiceImpl implements ConsultService {

    private final QuestionMapper questionMapper;
    private final AppointmentMapper appointmentMapper;
    private final ExpertMapper expertMapper;
    private final UserMapper userMapper;
    private final ExpertService expertService;

    // ==================================================================================
    // 1. 提问
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ask(Long userId, QuestionDTO dto) {
        Long expertId = dto.getExpertId();

        if (expertId != null) {
            Expert expert = expertMapper.selectById(expertId);
            if (expert == null) {
                throw new ResourceNotFoundException("指定的专家不存在");
            }
            // 不能向自己提问 —— 这不是安全漏洞，而是纯粹的脏数据：
            // 自己的问题自己回答，在列表里看着很怪，也没有咨询的意义
            if (Objects.equals(expert.getUserId(), userId)) {
                throw new BizException(ResultCode.CANNOT_OPERATE_SELF, "不能向自己提问");
            }
            // 必须确认这位专家确实已通过审核（t_expert 有记录 + 账号正常）。
            // 否则问题会发给一个答不了的人，永远停在"待回答"
            requireAvailableExpert(expert);
        }

        Question question = new Question();
        question.setUserId(userId);
        question.setExpertId(expertId);
        question.setTitle(dto.getTitle().trim());
        question.setContent(dto.getContent().trim());
        // 初始状态由代码决定，DTO 里没有 status 字段
        question.setStatus(0);
        questionMapper.insert(question);

        log.info("用户 {} 提问 {}：{}（{}）", userId, question.getId(), question.getTitle(),
                expertId == null ? "面向所有专家" : "指定专家 " + expertId);
        return question.getId();
    }

    // ==================================================================================
    // 2. 提问列表（按角色分视角）
    // ==================================================================================
    @Override
    public List<QuestionVO> myQuestions(Long userId, String role, Integer status) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();

        if (UserConst.ROLE_EXPERT.equals(role)) {
            // 专家视角 = 我的待办：别人点名问我的 + 面向所有专家的公开提问 + 我自己提的。
            Expert me = myExpertRecordOrNull(userId);
            if (me == null) {
                // 账号是 expert 角色但资质还没审核通过 —— 他看不到待办，
                // 只能看到自己作为普通用户提的问题
                wrapper.eq(Question::getUserId, userId);
            } else {
                wrapper.and(w -> w
                        .eq(Question::getExpertId, me.getId())   // 点名问我的
                        .or().isNull(Question::getExpertId)      // 面向所有专家的公开提问
                        .or().eq(Question::getUserId, userId));  // 我自己提的（也要能看到答案）
            }
        } else {
            // 普通用户视角 = 我提的问题
            wrapper.eq(Question::getUserId, userId);
        }

        wrapper.eq(status != null, Question::getStatus, status)
                // 待回答排前面（0 < 1），同状态下新问题在前
                .orderByAsc(Question::getStatus)
                .orderByDesc(Question::getCreateTime);

        return toQuestionVOs(questionMapper.selectList(wrapper));
    }

    /**
     * 实体列表 → VO 列表，并批量回填"被指定专家名"和"实际回答人名"。
     *
     * <p>这里需要两次批量查询（一次取 t_expert、一次取 t_user），而不是每个问题查两次。
     * 10 条问题在循环里查就是 20 次查询，批量则是 2 次 ——
     * 这是本模块里 N+1 风险最高的一个接口（因为要拼两张表的名字），所以特意写清楚。
     *
     * <p>实现上先建好 {@code Map<questionId, Question>} 再按 id 回填，
     * 而不是在 forEach 里用 {@code stream().filter().findFirst()} 反查原实体 ——
     * 后者是 O(n²)，20 条数据看不出差别，但写法本身就是错的，不该留。
     */
    private List<QuestionVO> toQuestionVOs(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<QuestionVO> vos = questions.stream().map(QuestionVO::from).collect(Collectors.toList());

        // 收集所有涉及的专家 id（被指定的 + 实际回答的），一次查完
        Set<Long> expertIds = new HashSet<>();
        questions.forEach(q -> {
            if (q.getExpertId() != null) {
                expertIds.add(q.getExpertId());
            }
            if (q.getAnswerBy() != null) {
                expertIds.add(q.getAnswerBy());
            }
        });
        if (expertIds.isEmpty()) {
            return vos;
        }
        Map<Long, String> expertNameMap = expertNameMap(expertIds);

        Map<Long, Question> byId = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        for (QuestionVO vo : vos) {
            Question q = byId.get(vo.getId());
            if (q == null) {
                continue;
            }
            if (q.getExpertId() != null) {
                vo.setExpertName(expertNameMap.get(q.getExpertId()));
            }
            if (q.getAnswerBy() != null) {
                vo.setAnswerByName(expertNameMap.get(q.getAnswerBy()));
            }
        }
        return vos;
    }

    /**
     * {@code t_expert.id → 专家姓名} 映射。
     *
     * <p>两步查表：t_expert 拿 userId，t_user 拿 name。
     * 优先用 real_name（专家的实名，比昵称正式），为空时退到 t_user.name。
     */
    private Map<Long, String> expertNameMap(Set<Long> expertIds) {
        if (expertIds == null || expertIds.isEmpty()) {
            return Map.of();
        }
        List<Expert> experts = expertMapper.selectBatchIds(expertIds);
        Set<Long> userIds = experts.stream()
                .map(Expert::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getName()) ? u.getName() : "未命名专家",
                        (a, b) -> a));

        return experts.stream().collect(Collectors.toMap(
                Expert::getId,
                e -> StringUtils.hasText(e.getRealName())
                        ? e.getRealName()
                        : userNameMap.getOrDefault(e.getUserId(), "未知专家"),
                (a, b) -> a));
    }

    // ==================================================================================
    // 3. 专家回答
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void answer(Long userId, Long questionId, AnswerDTO dto) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new ResourceNotFoundException("问题不存在");
        }
        // 只能回答一次。这条同时挡住了"改答案"—— 农户已经看到的结论不能被悄悄改掉
        if (!Objects.equals(question.getStatus(), 0)) {
            throw new BizException(ResultCode.QUESTION_ANSWERED, "该问题已被回答，不能重复回答");
        }
        // 只有已通过审核的专家能回答
        Expert me = expertService.requireApprovedExpert(userId);

        if (Objects.equals(question.getUserId(), userId)) {
            throw new BizException(ResultCode.CANNOT_OPERATE_SELF, "不能回答自己提出的问题");
        }
        // 定向提问：只有被指定的专家能答。公开提问（expertId 为空）任何人都能答
        if (question.getExpertId() != null && !Objects.equals(question.getExpertId(), me.getId())) {
            throw new BizException(ResultCode.FORBIDDEN, "该问题指定了其他专家回答");
        }

        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, questionId)
                .set(Question::getAnswer, dto.getAnswer().trim())
                .set(Question::getAnswerBy, me.getId())
                .set(Question::getStatus, 1)
                .set(Question::getAnsweredAt, LocalDateTime.now()));

        // 累加该专家的"已回答问题数"（专家列表页要展示，是农户选人的依据）。
        // 用 SQL 表达式而不是"查出来 +1 写回"，理由见 ArticleServiceImpl 的类注释
        expertMapper.update(null, new LambdaUpdateWrapper<Expert>()
                .eq(Expert::getId, me.getId())
                .setSql("answer_count = GREATEST(0, answer_count + 1)"));

        log.info("专家 {}（t_expert.id={}）回答了问题 {}", userId, me.getId(), questionId);
    }

    // ==================================================================================
    // 4. 发起预约
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAppointment(Long userId, AppointmentDTO dto) {
        Expert expert = expertMapper.selectById(dto.getExpertId());
        if (expert == null) {
            throw new ResourceNotFoundException("专家不存在");
        }
        if (Objects.equals(expert.getUserId(), userId)) {
            throw new BizException(ResultCode.CANNOT_OPERATE_SELF, "不能预约自己");
        }
        requireAvailableExpert(expert);

        if (!AppointmentConst.isValidType(dto.getType())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "预约方式只能是 online（线上）或 offline（线下）");
        }
        String type = dto.getType().trim().toLowerCase();

        // 时间解析：DateUtils.parse 兼容 "yyyy-MM-dd HH:mm:ss" 和 ISO 两种格式，
        // 解析失败会抛 IllegalArgumentException，这里包成业务异常让前端看到可读提示
        LocalDateTime appointmentTime;
        try {
            appointmentTime = DateUtils.parse(dto.getAppointmentTime());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.PARAM_ERROR, e.getMessage());
        }
        if (appointmentTime == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "预约时间不能为空");
        }
        if (!appointmentTime.isAfter(LocalDateTime.now())) {
            throw new BizException(ResultCode.APPOINTMENT_TIME_INVALID,
                    "预约时间必须晚于当前时间，请重新选择");
        }

        // 跨字段条件必填：线上要链接、线下要地点。
        // Bean Validation 表达不了"取决于另一个字段值"的规则，只能写在这里
        if (AppointmentConst.TYPE_ONLINE.equals(type) && !StringUtils.hasText(dto.getMeetingLink())) {
            throw new BizException(ResultCode.PARAM_ERROR, "线上预约必须填写会议链接");
        }
        if (AppointmentConst.TYPE_OFFLINE.equals(type) && !StringUtils.hasText(dto.getMeetingPlace())) {
            throw new BizException(ResultCode.PARAM_ERROR, "线下预约必须填写见面地点");
        }

        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setExpertId(expert.getId());
        appointment.setType(type);
        appointment.setAppointmentTime(appointmentTime);
        appointment.setPhone(dto.getPhone().trim());
        appointment.setDescription(dto.getDescription().trim());
        appointment.setMeetingLink(dto.getMeetingLink());
        appointment.setMeetingPlace(dto.getMeetingPlace());
        // 初始状态由代码决定，DTO 里没有 status 字段 ——
        // 否则前端可以 POST 一个 "status":"ACCEPTED" 绕过专家确认
        appointment.setStatus(AppointmentConst.STATUS_PENDING);
        appointmentMapper.insert(appointment);

        log.info("用户 {} 预约专家 {}（t_expert.id={}），{} 于 {}",
                userId, expert.getRealName(), expert.getId(), type, appointmentTime);
        return appointment.getId();
    }

    // ==================================================================================
    // 5. 预约列表（按角色分视角）
    // ==================================================================================
    @Override
    public List<AppointmentVO> myAppointments(Long userId, String role) {
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<>();

        if (UserConst.ROLE_EXPERT.equals(role)) {
            // 专家视角 = "别人约我的"。资质没通过的 expert 只能看到自己发起的
            Expert me = myExpertRecordOrNull(userId);
            if (me == null) {
                wrapper.eq(Appointment::getUserId, userId);
            } else {
                wrapper.and(w -> w
                        .eq(Appointment::getExpertId, me.getId())
                        .or().eq(Appointment::getUserId, userId));
            }
        } else {
            wrapper.eq(Appointment::getUserId, userId);
        }

        // 待处理排前面：专家打开页面就该先看到要做的事。
        // 这里不能用 orderByAsc(status) 按字符串排序 ——
        // 字典序是 ACCEPTED < PENDING < REJECTED，会让"已接受"排在"待处理"前面，
        // 正好反了。所以用 last() 写一段 CASE WHEN 显式指定顺序。
        // ⚠️ last() 是拼在 SQL 末尾的，不能再和 orderByAsc/Desc 混用（会生成两个 ORDER BY）
        wrapper.last("ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, "
                + "appointment_time DESC, id DESC");

        return toAppointmentVOs(appointmentMapper.selectList(wrapper));
    }

    /** 实体列表 → VO 列表，批量回填专家姓名 */
    private List<AppointmentVO> toAppointmentVOs(List<Appointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return List.of();
        }
        List<AppointmentVO> vos = appointments.stream().map(AppointmentVO::from)
                .collect(Collectors.toList());

        Set<Long> expertIds = appointments.stream()
                .map(Appointment::getExpertId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = expertNameMap(expertIds);
        vos.forEach(vo -> vo.setExpertName(nameMap.getOrDefault(vo.getExpertId(), "未知专家")));
        return vos;
    }

    // ==================================================================================
    // 6. 专家处理预约
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAppointment(Long userId, Long appointmentId, AppointmentHandleDTO dto) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new ResourceNotFoundException("预约不存在");
        }
        // 状态校验放在最前面：已处理的预约对任何人（包括该专家本人）都不能再动
        if (!AppointmentConst.STATUS_PENDING.equalsIgnoreCase(appointment.getStatus())) {
            throw new BizException(ResultCode.APPOINTMENT_HANDLED,
                    "该预约已处理为「" + AppointmentConst.statusName(appointment.getStatus())
                            + "」，不能重复操作");
        }
        if (!AppointmentConst.isValidHandleStatus(dto.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "处理结果只能是 ACCEPTED（接受）或 REJECTED（拒绝）");
        }

        // 必须是被预约的那位专家本人 —— 角色对不够，还要归属对
        Expert me = expertService.requireApprovedExpert(userId);
        if (!Objects.equals(appointment.getExpertId(), me.getId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能处理预约给自己的请求");
        }

        String status = dto.getStatus().trim().toUpperCase();
        appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, appointmentId)
                .set(Appointment::getStatus, status)
                .set(Appointment::getHandledAt, LocalDateTime.now()));

        log.info("专家 {} 将预约 {} 处理为 {}", userId, appointmentId, status);
    }

    // ==================================================================================
    // 私有辅助
    // ==================================================================================

    /**
     * 取当前用户的专家资质记录，<b>没有则返回 null</b>（不抛异常）。
     *
     * <p>和 {@code ExpertService.requireApprovedExpert} 的区别：
     * 那个是"你必须是专家，否则报错"（回答提问、处理预约时必须用），
     * 这个只用于"列表按角色分视角" —— 一个 role=expert 但还没过审的用户
     * 访问提问列表不该报错，只是看不到待办而已。
     */
    private Expert myExpertRecordOrNull(Long userId) {
        return expertMapper.selectOne(new LambdaQueryWrapper<Expert>()
                .eq(Expert::getUserId, userId)
                .last("LIMIT 1"));
    }

    /**
     * 确认某位专家当前"可咨询"：账号存在且正常。
     *
     * <p>与 {@code ExpertService.requireApprovedExpert(userId)} 的区别是：
     * 那个按<b>用户 id</b> 查（判断"我是不是专家"），
     * 这个按<b>专家记录</b>查（判断"这个专家现在能不能被咨询"）。两个方向都需要。
     */
    private void requireAvailableExpert(Expert expert) {
        User user = userMapper.selectById(expert.getUserId());
        if (user == null || !Objects.equals(user.getStatus(), UserConst.STATUS_NORMAL)) {
            throw new BizException(ResultCode.EXPERT_NOT_AVAILABLE, "该专家当前不可咨询");
        }
    }
}
