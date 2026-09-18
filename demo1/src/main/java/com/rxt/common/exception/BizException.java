package com.rxt.common.exception;

import com.rxt.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常 —— 所有"能预料到的业务失败"都抛它，由 GlobalExceptionHandler 统一翻译成 Result。
 *
 * <p><b>为什么不直接 return Result.fail(...)？</b>
 * 因为 Service 层的职责是"办事"，不是"拼响应"。一旦 Service 开始 return Result，
 * 所有方法的返回值都被 Result 包住，业务数据就得从 {@code result.getData()} 里往外掏，
 * 而且事务回滚也失效了（抛异常才会回滚，return 不会）。
 * 正确做法：Service 全程抛异常，Controller 全程返回正常类型，异常在切面里统一收口。
 *
 * <p><b>两个构造器的区别</b>：
 * <ul>
 *   <li>{@code new BizException(ResultCode.ACCOUNT_DISABLED)} —— 用字典里的标准文案；
 *   <li>{@code new BizException(ResultCode.ACCOUNT_DISABLED, "账号待审核，请等待管理员审核")}
 *       —— <b>错误码按字典、文案自己写</b>。这是最常用的写法：同一个 code（前端只认 code）
 *       可以对应好几种具体原因（用户需要看到具体是哪种）。
 * </ul>
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码，前端拦截器靠它做分支判断 */
    private final int code;

    /** 错误码枚举，方便全局异常处理器取 httpStatus */
    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
    }

    /** 错误码走字典，文案自定义（最常用） */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
    }

    /** 兜底：没指定错误码时按"系统内部错误"处理 */
    public BizException(String message) {
        this(ResultCode.SYSTEM_ERROR, message);
    }
}
