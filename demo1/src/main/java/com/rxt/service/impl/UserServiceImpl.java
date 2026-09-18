package com.rxt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rxt.common.ResultCode;
import com.rxt.common.constant.UserConst;
import com.rxt.common.exception.BizException;
import com.rxt.dto.LoginDTO;
import com.rxt.dto.RegisterDTO;
import com.rxt.dto.UpdateInfoDTO;
import com.rxt.dto.UpdatePasswordDTO;
import com.rxt.entity.User;
import com.rxt.mapper.UserMapper;
import com.rxt.security.JwtUtil;
import com.rxt.service.UserService;
import com.rxt.vo.LoginVO;
import com.rxt.vo.RegisterVO;
import com.rxt.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;


    @Value("${rxt.mock.verify-code}")
    private String mockVerifyCode;

    /** 连续登录失败上限 */
    @Value("${rxt.security.max-fail-count}")
    private int maxFailCount;

    /** 锁定时长（分钟） */
    @Value("${rxt.security.lock-minutes}")
    private int lockMinutes;

    // ==================================================================================
    // 1. 注册
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterDTO dto) {
        // ① 验证码（SRS 表11：Mock 固定为 111111）
        if (!mockVerifyCode.equals(dto.getVerifyCode())) {
            throw new BizException(ResultCode.VERIFY_CODE_ERROR, "验证码错误，请输入 " + mockVerifyCode);
        }

        // ② 手机号唯一性
        //    selectCount 返回 Long，用 exists 语义，不需要把整行查出来
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该手机号已注册");
        }

        // ③ 角色白名单校验
        //    这一行是安全边界，不能省：不校验的话，任何人都能 POST 一个 "role":"admin"
        //    把自己变成平台管理员，M7 的权限体系直接失效。
        String role = dto.getRole();
        if (!UserConst.REGISTER_ROLES.contains(role)) {
            throw new BizException(ResultCode.PARAM_ERROR, "注册角色只能是农户或农业专家");
        }

        // ④ 落库
        User user = new User();
        user.setPhone(dto.getPhone());
        // BCrypt 加密。数据库里永远不存明文，也不需要单独的 salt 列（盐在密文里）
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(role);
        // 手机号后 4 位拼个默认昵称，避免个人中心一开始是空白
        user.setName("用户" + dto.getPhone().substring(dto.getPhone().length() - 4));
        // 专家待审核，农户/买家直接可用（SRS 表11 业务规则）
        user.setStatus(UserConst.ROLE_EXPERT.equals(role) ? UserConst.STATUS_PENDING : UserConst.STATUS_NORMAL);
        user.setFailCount(0);
        userMapper.insert(user);
        // insert 之后 user.getId() 已经被 MyBatis-Plus 回填（@TableId(type = IdType.AUTO) 的作用）

        log.info("新用户注册成功：phone={}, role={}, status={}", user.getPhone(), user.getRole(), user.getStatus());

        // ⑤ 只有"直接可用"的账号才签发 Token（自动登录）；专家要等审核，给 null
        String token = null;
        if (UserConst.STATUS_NORMAL == user.getStatus()) {
            token = jwtUtil.generate(user.getId(), user.getPhone(), user.getRole());
        }
        return RegisterVO.of(user, token);
    }

    // ==================================================================================
    // 2. 登录
    // ==================================================================================
    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));

        // ① 账号不存在 —— 故意和"密码错误"返回完全一样的结果。
        //    如果这里提示"账号不存在"、那里提示"密码错误"，攻击者就能靠文案差异
        //    枚举出哪些手机号在平台注册过（用户信息泄露）。
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        // ② 锁定检查（必须在验密码之前）
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            long remainMinutes = Duration.between(now, user.getLockUntil()).toMinutes() + 1;
            // 锁定期内不再累加失败次数，避免"一直试就一直续锁"
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                    "账号已锁定，请 " + remainMinutes + " 分钟后重试");
        }

        // ③ 状态检查
        assertLoginable(user);

        // ④ 密码校验
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            handleLoginFail(user);
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        // ⑤ 登录成功：清零失败计数、解除锁定、记录登录时间
        //    ⚠️ 这里必须用 LambdaUpdateWrapper，不能图省事写 updateById：
        //    MyBatis-Plus 默认"null 字段不进 SET"，而我们要把 lock_until 置成 null
        //    （解除锁定），用 updateById 的话 null 会被直接跳过，锁永远解不掉。
        //    写 .set(字段, null) 才会真正生成 "lock_until = null"。
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getFailCount, 0)
                .set(User::getLockUntil, null)
                .set(User::getLastLoginAt, now));

        log.info("用户登录成功：phone={}, role={}", user.getPhone(), user.getRole());

        // ⑥ 用内存里这份 user 生成 Token（它已经被改成最新的状态，不用再查库）
        String token = jwtUtil.generate(user.getId(), user.getPhone(), user.getRole());
        return LoginVO.of(token, jwtUtil.getExpireSeconds(), user);
    }

    // ==================================================================================
    // 3. 获取当前用户信息
    // ==================================================================================
    @Override
    public UserVO getInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            // Token 里带着一个数据库里已经不存在的 id（例如账号被物理删除）——属于异常情况
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return UserVO.from(user);
    }

    // ==================================================================================
    // 4. 更新个人信息
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateInfo(Long userId, UpdateInfoDTO dto) {
        User exist = userMapper.selectById(userId);
        if (exist == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        // 一个字段都没传（全是 null）时直接返回，不执行 UPDATE。
        // 否则 MyBatis-Plus 会生成 "UPDATE t_user SET WHERE id=?" 这种语法错误的 SQL。
        if (isAllBlank(dto)) {
            return UserVO.from(exist);
        }

        // 这里只 new 一个"干净"的 User，只 set 要改的字段：
        // 一是没 set 的字段是 null，会被 MyBatis-Plus 跳过，天然实现了"部分更新"；
        // 二是规避了"把查出来的 exist 直接改再 updateById"的写法——
        //    那种写法会把 phone、password、role 等所有字段一起写回去，一旦并发修改就会互相覆盖。
        User update = new User();
        update.setId(userId);
        update.setName(dto.getName());
        update.setEmail(dto.getEmail());
        update.setProvince(dto.getProvince());
        update.setCity(dto.getCity());
        update.setDistrict(dto.getDistrict());
        update.setAvatar(dto.getAvatar());
        // 手机号不在这里 set —— DTO 里根本没有这个字段，从结构上保证"手机号不可修改"

        userMapper.updateById(update);

        // 返回更新后的完整信息（重新查一次，拿到数据库里的最新值而不是我们拼的）
        return UserVO.from(userMapper.selectById(userId));
    }

    // ==================================================================================
    // 5. 修改密码
    // ==================================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, UpdatePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        // ① 原密码必须正确
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原密码错误");
        }

        // ② 新密码不能与原密码相同（拿明文互比即可，因为已经确认原密码验证通过了）
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        // ③ 写新密码。密码字段用 .set() 显式更新，避免 updateById 的 null 跳过策略带来意外
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword())));

        log.info("用户修改密码成功：userId={}", userId);
        // ⚠️ SRS 表13 还要求"强制下线"。JWT 是无状态的，服务端没有会话可以销毁，
        //    真正的强制下线需要 Token 黑名单（Redis 或表），不在 P1 范围内。
        //    当前由前端清掉 localStorage 里的 Token 达成，联调时要和前端确认这一条。
    }

    // ==================================================================================
    // 私有辅助方法
    // ==================================================================================

    /**
     * 登录状态校验：0 待审核 / 2 已拒绝 / 3 已禁用，都不允许登录。
     *
     * <p>这三种情况共用错误码 1003，但给不同的提示文案——前端只认 code 做分支，
     * 用户需要看到具体原因。这是 {@link BizException} 那个"两个参数构造器"的典型用法。
     */
    private void assertLoginable(User user) {
        Integer status = user.getStatus();
        if (status == null || status == UserConst.STATUS_DISABLED) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (status == UserConst.STATUS_PENDING) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "账号待审核，请等待管理员审核通过后登录");
        }
        if (status == UserConst.STATUS_REJECTED) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "账号审核未通过，请联系管理员");
        }
    }

    /**
     * 登录失败计数 + 触发锁定。
     *
     * <p>规则（SRS 表12）：连续输错 5 次，锁定 30 分钟。
     *
     * <p>锁定那一刻把 fail_count 归零，这样 30 分钟解锁后用户又有完整的 5 次机会。
     * 如果不归零（保持 5），解锁后下一次输错就立刻 6>=5 再次锁定，等于永久锁定，用户会崩溃。
     */
    private void handleLoginFail(User user) {
        int currentFail = user.getFailCount() == null ? 0 : user.getFailCount();
        int failCount = currentFail + 1;

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, user.getId());

        if (failCount >= maxFailCount) {
            wrapper.set(User::getFailCount, 0)
                    .set(User::getLockUntil, LocalDateTime.now().plusMinutes(lockMinutes));
            log.warn("账号连续登录失败 {} 次，已锁定 {} 分钟：phone={}", failCount, lockMinutes, user.getPhone());
        } else {
            wrapper.set(User::getFailCount, failCount);
            log.warn("账号登录失败，累计 {}/{} 次：phone={}", failCount, maxFailCount, user.getPhone());
        }

        userMapper.update(null, wrapper);
    }

    /** 判断更新入参是否所有字段都为空 */
    private boolean isAllBlank(UpdateInfoDTO dto) {
        return isBlank(dto.getName())
                && isBlank(dto.getEmail())
                && isBlank(dto.getProvince())
                && isBlank(dto.getCity())
                && isBlank(dto.getDistrict())
                && isBlank(dto.getAvatar());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
