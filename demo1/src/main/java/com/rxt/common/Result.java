package com.rxt.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体 —— 所有接口的返回外壳，前端只需按这一套结构解析。
 *
 * <p>响应形状（概要设计 5.2.1 约定的结构）：
 * <pre>
 * { "code": 0, "message": "操作成功", "data": { ... } }
 * </pre>
 *
 * <p><b>关键约定：HTTP 状态码和业务错误码是两套东西，不要混。</b>
 * <ul>
 *   <li>{@code code=0} 表示业务成功，此时 HTTP 通常也是 200；</li>
 *   <li>{@code code=1002} 表示"手机号或密码错误"，HTTP 对应 401；</li>
 *   <li>{@code code=1001} 表示参数校验失败，HTTP 对应 400。</li>
 * </ul>
 * 前端判断成功<b>只看 {@code code === 0}</b>，不要去看 HTTP 状态码——
 * 因为网络层异常（502、超时）也会是非 200，两者要分开处理。
 *
 * <p><b>为什么用泛型 {@code <T>}？</b>这样 {@code Result<UserVO>}、
 * {@code Result<PageResult<UserVO>>} 都能共用同一个外壳，
 * 而且 Swagger 能推导出 data 里的真实字段结构。
 */
@Data
public class Result<T> implements Serializable {

    /** 业务错误码，0 表示成功 */
    private Integer code;

    /** 提示文案，可直接展示给用户 */
    private String message;

    /** 业务数据。失败时为 null */
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 成功 ====================

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ==================== 失败 ====================

    /** 用字典里的标准文案 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 错误码走字典，文案自定义。最常用——同一个 code 可以对应多种具体原因 */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 直接给数字码，用于极少数无法映射到枚举的场景 */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 是否成功。给内部代码用的便捷判断 */
    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode() == this.code;
    }
}
