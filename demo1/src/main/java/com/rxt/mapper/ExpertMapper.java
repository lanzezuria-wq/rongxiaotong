package com.rxt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.entity.Expert;

/**
 * 专家扩展表 Mapper。空的——单表 CRUD 全在 {@code BaseMapper} 里（用法同 UserMapper）。
 *
 * <p>不用再写 {@code @Mapper} 注解：启动类上的 {@code @MapperScan("com.rxt.mapper")}
 * 已经把整个包扫过了。
 */
public interface ExpertMapper extends BaseMapper<Expert> {
}
