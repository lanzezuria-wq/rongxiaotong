package com.rxt.assist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rxt.assist.entity.ArticleComment;

/**
 * 文章评论表 Mapper（M5）。
 *
 * <p>空的 —— 单表 CRUD 全在 {@code BaseMapper} 里。
 * 需要复杂查询（连表、聚合）时再在这里加方法与 XML，现在不加是为了不为想象中的需求付成本。
 */
public interface ArticleCommentMapper extends BaseMapper<ArticleComment> {
}
