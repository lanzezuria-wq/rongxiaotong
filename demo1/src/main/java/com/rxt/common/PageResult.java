package com.rxt.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应体 —— 所有列表接口的 data 结构。
 *
 * <p>它<b>不替代</b> {@link Result}，而是被 Result 包在里面：
 * <pre>
 * Result&lt;PageResult&lt;UserVO&gt;&gt;
 *   └── data: { records: [...], total: 42, page: 1, size: 10, pages: 5 }
 * </pre>
 *
 * <p><b>为什么要自己包一层，不直接把 MyBatis-Plus 的 {@code IPage} 返回出去？</b>
 * 因为 {@code IPage} 的实现类（{@code Page}）带了一堆前端用不到的内部字段
 * （{@code searchCount}、{@code optimizeCountSql}、{@code orders}…），
 * 直接返回会让响应体又大又难用，而且把持久层的类型泄露到了 API 契约上。
 * 转成这个 5 字段的结构，前端只需要认 {@code records} 和 {@code total}。
 *
 * <p>{@link #of(IPage, Function)} 是这层转换的关键：它接收"实体分页"和"实体→VO 的转换函数"，
 * 输出"VO 分页"。这样 Service 里一行就能把 {@code IPage<User>} 变成
 * {@code PageResult<UserVO>}，同时<b>保证列表里每一项都过了 VO 转换</b>——
 * 不会出现"忘了转 VO 导致密码被返回"的事故。
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? Collections.emptyList() : records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return result;
    }

    /**
     * 把 MyBatis-Plus 的实体分页转换为 VO 分页。
     *
     * @param source 实体分页对象（{@code mapper.selectPage(...)} 的返回值）
     * @param mapper 实体 → VO 的转换函数，通常传 {@code UserVO::from}
     */
    public static <S, T> PageResult<T> of(IPage<S> source, Function<S, T> mapper) {
        List<T> records = source.getRecords().stream().map(mapper).collect(Collectors.toList());
        return of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    /** 空分页，用于"条件查出 0 条且不想查库"的短路场景 */
    public static <T> PageResult<T> empty(long page, long size) {
        return of(Collections.emptyList(), 0, page, size);
    }
}
