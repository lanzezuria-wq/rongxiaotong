package com.rxt.common.exception;

import com.rxt.common.ResultCode;

/**
 * 资源不存在异常。
 *
 * <p>对应概要设计 7.1.1 全局异常处理表里的 {@code ResourceNotFoundException}。
 *
 * <p><b>⚠️ 这里有一个文档矛盾，必须说明：</b>
 * <ul>
 *   <li>概要设计 7.1.1 表写：{@code ResourceNotFoundException → 1004（资源不存在）}；</li>
 *   <li>但概要设计 5.2.2 表里 1004 已经被分配给了"验证码错误"，SRS 表54 也一致。</li>
 * </ul>
 * 两处冲突。本项目取 <b>5.2.2 为准</b>（它是专门的"错误码规范"表，且带 HTTP 状态码映射），
 * 所以"资源不存在"使用补充码 {@link ResultCode#RESOURCE_NOT_FOUND}（1008，HTTP 404）。
 * <b>这一条需要和组内定稿</b>——如果最终决定沿用文档的 1004，改这里的构造即可，业务代码不用动。
 *
 * <p>为什么不和 {@link BizException} 合并？因为它们的 HTTP 状态码不同（404 vs 400/401/403），
 * 分开定义能让全局异常处理器按类型精确映射，也能让日志里一眼看出是"找不到"还是"业务拒绝"。
 */
public class ResourceNotFoundException extends BizException {

    public ResourceNotFoundException(String message) {
        super(ResultCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException() {
        super(ResultCode.RESOURCE_NOT_FOUND);
    }
}
