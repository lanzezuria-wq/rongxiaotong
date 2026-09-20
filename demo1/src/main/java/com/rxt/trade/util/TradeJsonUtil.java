package com.rxt.trade.util;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * 交易模块的 JSON 字符串 ↔ Java 对象转换工具。
 *
 * <p><b>为什么需要它？</b>因为 t_product 的两个字段（{@code images}、{@code sku_list}）
 * 在库里是 VARCHAR，存的是 JSON 字符串，而 API 契约要求它们以真正的数组形式返回。
 * 这个"字符串 ↔ 数组"的转换点如果散落在各个 VO 里，容易写歪（漏判 null、异常没吞）。
 * 集中到一处。
 *
 * <p><b>为什么解析失败要返回空集合而不是抛异常？</b>
 * 因为这是<b>读路径</b>。库里的 JSON 万一被人工改坏了，正确的反应是
 * "这个商品的图片显示不出来"，而不是"整个商品列表接口 500"。
 * 一条脏数据不该拖垮整页。
 *
 * <p>反向（Java 对象 → JSON 字符串）用在写入路径，这时<b>应该</b>抛异常——
 * 写进去的必须是合法 JSON，否则会污染数据库。所以 {@link #toJson} 不吞异常。
 */
@Slf4j
public final class TradeJsonUtil {

    private TradeJsonUtil() {
    }

    /**
     * 把 JSON 数组字符串解析成 List。
     *
     * @param json 形如 {@code ["url1","url2"]} 的字符串，允许为 null 或空白
     * @return 解析结果；null/空白/格式错误时返回<b>空列表</b>，绝不返回 null
     */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = JSONUtil.toList(json, String.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            // 只记 warn 不抛：读路径要能容错
            log.warn("JSON 数组解析失败，按空处理。原始值前 100 字符：{}",
                    json.length() > 100 ? json.substring(0, 100) + "..." : json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 把 JSON 数组字符串解析成指定类型的对象列表。
     *
     * <p>用于 {@code sku_list}：{@code [{"spec":"5斤装","price":25,"stock":100}]}。
     *
     * @param clazz 元素类型，例如 {@code ProductVO.SkuVO.class}
     */
    public static <T> List<T> parseList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<T> list = JSONUtil.toList(json, clazz);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            log.warn("JSON 列表解析失败，按空处理（目标类型 {}）。原始值前 100 字符：{}",
                    clazz.getSimpleName(),
                    json.length() > 100 ? json.substring(0, 100) + "..." : json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 把对象序列化成 JSON 字符串，用于写入数据库。
     *
     * <p>与上面两个方法相反，这里<b>不吞异常</b>——写入路径必须是合法 JSON。
     * 如果传进来一个序列化不了的对象，说明是代码 bug，应该立刻暴露。
     *
     * @return 序列化结果；传入 null 时返回 null（让调用方决定存 null 还是空串）
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSONUtil.toJsonStr(obj);
    }

    /**
     * 取 JSON 数组里的第一项，用作列表页的封面图。
     *
     * <p>商品最多 6 张图，列表页只需要一张。抽成方法避免每个 VO 都写一遍
     * {@code images.isEmpty() ? null : images.get(0)}。
     */
    public static String firstOrNull(List<String> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }
}
