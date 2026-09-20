package com.rxt.assist.constant;

import java.util.Set;

/**
 * 专家预约常量 —— M5 专家助力（预约部分）。
 *
 * <p><b>为什么状态用字符串而不用数字？</b>和 P1 的 {@code AuditConst} 同一个理由：
 * 这几个值是<b>接口契约的一部分</b>（OpenAPI 里 AppointmentVO.status 的 enum 就是
 * {@code [PENDING, ACCEPTED, REJECTED]}），前端提交 {@code "ACCEPTED"} 比提交
 * {@code 1} 一眼就懂；而且"待处理"这个状态不能由处理接口设置，
 * 用字符串枚举从取值上就天然排除了（数字 0/1/2 很容易把 0 传进去）。
 *
 * <p>数据库 t_appointment.status 是 VARCHAR，直接存这串大写字符串，不做数字映射 ——
 * 少一层转换就少一处出错的地方。
 */
public final class AppointmentConst {

    private AppointmentConst() {
    }

    // ==================== 状态 ====================

    /** 待处理 —— 提交预约后的初始状态 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已接受 */
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    /** 已拒绝 */
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * 专家处理预约时<b>允许设置</b>的状态。
     *
     * <p>故意不含 PENDING —— 让"处理"接口无法把已处理的预约退回待处理，
     * 这和专家审核、文章审核、银行审批的"结果不可撤回"是同一条原则。
     */
    public static final Set<String> HANDLE_STATUSES = Set.of(STATUS_ACCEPTED, STATUS_REJECTED);

    /** 判断专家处理预约时传的状态是否合法 */
    public static boolean isValidHandleStatus(String status) {
        return status != null && HANDLE_STATUSES.contains(status.toUpperCase());
    }

    /** 状态 → 中文展示名 */
    public static String statusName(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status.toUpperCase()) {
            case STATUS_PENDING -> "待处理";
            case STATUS_ACCEPTED -> "已接受";
            case STATUS_REJECTED -> "已拒绝";
            default -> "未知";
        };
    }

    // ==================== 预约方式 ====================

    /** 线上（视频会议等），此时 meetingLink 必填 */
    public static final String TYPE_ONLINE = "online";
    /** 线下（到田间地头等），此时 meetingPlace 必填 */
    public static final String TYPE_OFFLINE = "offline";

    public static final Set<String> TYPES = Set.of(TYPE_ONLINE, TYPE_OFFLINE);

    public static boolean isValidType(String type) {
        return type != null && TYPES.contains(type.toLowerCase());
    }

    /** 方式 → 中文展示名 */
    public static String typeName(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type.toLowerCase()) {
            case TYPE_ONLINE -> "线上";
            case TYPE_OFFLINE -> "线下";
            default -> "未知";
        };
    }
}
