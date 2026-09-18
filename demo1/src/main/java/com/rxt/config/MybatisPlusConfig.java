package com.rxt.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p><b>⚠️ 这个类不写会出"静默错误"，非常坑。</b>
 * MyBatis-Plus 的分页<b>不是自动生效的</b>，必须注册 {@link PaginationInnerInterceptor}。
 * 不注册的话，{@code mapper.selectPage(page, wrapper)} 不会报错，
 * 但它<b>不会往 SQL 里拼 LIMIT</b>——结果是把全表查出来塞进 {@code Page} 对象，
 * 而且 {@code total} 还是对的，肉眼看接口返回完全正常。
 * 要等数据量大到内存扛不住才会暴露。
 *
 * <p><b>两个插件的作用：</b>
 * <ul>
 *   <li>{@link PaginationInnerInterceptor} —— 分页。指定 {@link DbType#MYSQL} 后，
 *       它会按 MySQL 方言生成 {@code LIMIT ?, ?}。它也负责"先查 count 再查数据"的两段式查询。</li>
 *   <li>{@link BlockAttackInnerInterceptor} —— <b>防全表更新/删除</b>。
 *       任何没有 WHERE 条件的 update / delete 会被它直接抛异常拦下。
 *       这是防手滑的保险丝：写错一个 Wrapper，本来会清空整张用户表，有了它只是报个错。</li>
 * </ul>
 *
 * <p><b>为什么分页插件依赖 jsqlparser？</b>因为它要解析你写的 SQL、
 * 在正确的语法位置插入 LIMIT 子句（还要处理 UNION、子查询等复杂情况），
 * 这需要真正的 SQL 解析器。所以 pom 里必须额外引 {@code mybatis-plus-jsqlparser}，
 * 只引 starter 会在运行到分页时报 {@code ClassNotFoundException}。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数上限。防止前端传 size=999999 把数据库拖垮
        pagination.setMaxLimit(200L);
        // 超出最大页数时返回空列表而不是回到第一页
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
