package com.rxt.common.exception;

import com.rxt.common.Result;
import com.rxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 整个地基的收口处。
 *
 * <p><b>它的存在意义：让 Controller 和 Service 里彻底不用写 try-catch。</b>
 * Service 发现问题就 {@code throw new BizException(...)}，异常一路往上抛到这里，
 * 由这里统一翻译成 {@link Result} + 正确的 HTTP 状态码。
 *
 * <p>{@code @RestControllerAdvice} 是 {@code @ControllerAdvice} + {@code @ResponseBody} 的组合，
 * 表示"拦截所有 Controller 抛出的异常，并把返回值序列化成 JSON 写回响应体"。
 *
 * <p><b>三条工程纪律（都在下面的实现里落地）：</b>
 * <ol>
 *   <li><b>HTTP 状态码跟随业务错误码</b>，不要一律返回 200。这样浏览器 Network 面板、
 *       网关、前端拦截器都能第一眼看出是 401 还是 400。</li>
 *   <li><b>Java 堆栈绝不进响应体</b>（概要设计 8.3 明确要求）。堆栈只写日志，
 *       返回给前端的是 {@code code=1000 + "系统内部错误"}——堆栈里有表名、SQL、包路径，
 *       泄露出去等于给攻击者送情报。</li>
 *   <li><b>预期的业务异常用 warn、意外的系统异常用 error</b>。否则日志里全是 error，
 *       真正的故障会被淹没（用户密码打错不该触发告警）。</li>
 * </ol>
 *
 * <p><b>关于 Spring Security 的三类异常</b>（7.1.1 表里列了它们）：
 * 它们<b>不一定</b>能走到这里。发生在过滤器链里（还没进 Controller）的认证/授权失败，
 * 会被 Spring Security 自己的 {@code AuthenticationEntryPoint} / {@code AccessDeniedHandler}
 * 拦下——所以那两个类里也要写一套同样的 Result 响应，两处都要有，缺一个就会出现
 * "有时返回 Result 格式、有时返回 Security 默认格式"的诡异现象。
 * 而 {@code @PreAuthorize} 在方法级抛出的 {@code AccessDeniedException} 是能走到这里的，
 * 所以下面照样要处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（含 ResourceNotFoundException / TooManyRequestsException 两个子类）。
     *
     * <p>错误码和 HTTP 状态码都从异常持有的 {@link ResultCode} 枚举里取，
     * 所以新增错误码时只要改枚举，不用改这里。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e, HttpServletRequest request) {
        ResultCode rc = e.getResultCode();
        log.warn("业务异常 [{} {}] code={} msg={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return ResponseEntity.status(rc.getHttpStatus())
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * {@code @RequestBody} 上的 {@code @Valid} 校验失败 —— 错误码 1001，HTTP 400。
     *
     * <p>把所有字段的错误拼成一条提示。这里<b>只取前 3 条</b>：
     * 一个表单可能有 6 个字段同时必填，全拼出来前端弹窗放不下。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .limit(3)
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", message);
        return badRequest(message);
    }

    /** 表单/查询参数绑定失败（非 @RequestBody 场景），同样归到 1001 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .limit(3)
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败：{}", message);
        return badRequest(message);
    }

    /** 方法参数上的 {@code @Validated} 约束失败（例如 @PathVariable @Min(1)） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .limit(3)
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("约束校验失败：{}", message);
        return badRequest(message);
    }

    /** 少传了必填的查询参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数：{}", e.getParameterName());
        return badRequest("缺少必填参数：" + e.getParameterName());
    }

    /** 参数类型不对，例如把 {@code page=abc} 传进了 {@code int page} */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误：{}", e.getName());
        return badRequest("参数 " + e.getName() + " 的类型不正确");
    }

    /** 请求体不是合法 JSON（少了大括号、多了逗号等） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return badRequest("请求体格式不正确，请检查是否为合法 JSON");
    }

    /** HTTP 方法用错了，例如把 POST 接口用 GET 调用 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return ResponseEntity.status(405)
                .body(Result.fail(ResultCode.PARAM_ERROR, "请求方法不支持：" + e.getMethod()));
    }

    /** 访问了不存在的路径 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return ResponseEntity.status(404)
                .body(Result.fail(ResultCode.RESOURCE_NOT_FOUND, "接口不存在：" + e.getResourcePath()));
    }

    /**
     * 唯一索引冲突 —— 数据库层的兜底。
     *
     * <p>业务层通常已经先查过一遍"手机号是否已注册"，但不做这个处理就会漏：
     * 两个请求同时通过"查重"检查、再同时 INSERT 时，后一个必然撞唯一索引
     * （这叫并发竞态，查重挡不住）。此时如果落到最后的 {@code Exception} 分支，
     * 用户会看到"系统内部错误"，而实际上这是一次可解释的失败。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突：{}", e.getMostSpecificCause().getMessage());
        return badRequest("数据已存在，请勿重复提交");
    }

    /** 上传文件超过限制，对应 SRS 表14"头像大小不能超过 5MB" */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件过大：{}", e.getMessage());
        return badRequest("图片大小不能超过 5MB");
    }

    // ==================== Spring Security 的异常 ====================

    /** 用户名或密码错误（如果 Service 把认证委托给了 AuthenticationManager，会走到这里） */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentials(BadCredentialsException e) {
        log.warn("认证失败：BadCredentials");
        return ResponseEntity.status(401).body(Result.fail(ResultCode.LOGIN_FAILED));
    }

    /** 账号被禁用 */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Result<Void>> handleDisabled(DisabledException e) {
        log.warn("认证失败：账号已禁用");
        return ResponseEntity.status(403).body(Result.fail(ResultCode.ACCOUNT_DISABLED));
    }

    /** 其他认证类异常，统一按 1002 处理 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("认证失败：{}", e.getMessage());
        return ResponseEntity.status(401).body(Result.fail(ResultCode.LOGIN_FAILED, "认证失败，请重新登录"));
    }

    /** 权限不足 —— 主要由 @PreAuthorize 在方法级抛出 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return ResponseEntity.status(403).body(Result.fail(ResultCode.FORBIDDEN));
    }

    // ==================== 兜底 ====================

    /**
     * 最后一道防线：任何没被上面接住的异常都落在这里。
     *
     * <p>返回给前端的是<b>泛化的</b> "系统内部错误"，堆栈只写日志。
     * 如果把 {@code e.getMessage()} 直接返回，可能把 SQL 片段、表名、文件路径泄露出去。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统未预期异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(500).body(Result.fail(ResultCode.SYSTEM_ERROR));
    }

    // ==================== 私有辅助 ====================

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(400).body(Result.fail(ResultCode.PARAM_ERROR, message));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + "：" + error.getDefaultMessage();
    }
}
