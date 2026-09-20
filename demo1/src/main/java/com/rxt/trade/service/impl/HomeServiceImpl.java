package com.rxt.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rxt.entity.Article;
import com.rxt.entity.DictItem;
import com.rxt.mapper.ArticleMapper;
import com.rxt.mapper.DictItemMapper;
import com.rxt.trade.constant.ProductConst;
import com.rxt.trade.entity.Product;
import com.rxt.trade.mapper.ProductMapper;
import com.rxt.trade.service.HomeService;
import com.rxt.trade.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页服务实现。
 *
 * <p><b>这个实现类跨了两个模块的 Mapper</b>：商品（P2 的 t_product）和
 * 字典、文章（P1 的 t_dict / t_article）。单模块工程下直接注入就行，
 * 但要清楚这个耦合点——如果以后真拆成 Maven 多模块，
 * 这里需要改成通过 rxt-common 暴露的接口调用，不能让 rxt-trade 直连 rxt-system 的 Mapper。
 *
 * <p>全部用 {@code LambdaQueryWrapper} 而不是字符串字段名，
 * 好处是字段名写错<b>编译期就报错</b>，而字符串写错只能运行时才发现。
 */
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final ProductMapper productMapper;
    private final DictItemMapper dictItemMapper;
    private final ArticleMapper articleMapper;

    /** 轮播图字典类型 */
    private static final String BANNER_TYPE = "banner";

    /** 推荐商品个数 */
    private static final int RECOMMEND_LIMIT = 12;

    /** 热门文章个数 */
    private static final int HOT_ARTICLE_LIMIT = 5;

    /** 文章状态：已发布（0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架） */
    private static final int ARTICLE_PUBLISHED = 1;

    @Override
    public List<Map<String, Object>> banners() {
        List<DictItem> items = dictItemMapper.selectList(
                new LambdaQueryWrapper<DictItem>()
                        .eq(DictItem::getType, BANNER_TYPE)
                        .eq(DictItem::getEnabled, 1)
                        .orderByAsc(DictItem::getSort));

        List<Map<String, Object>> result = new ArrayList<>(items.size());
        for (DictItem item : items) {
            // 用 LinkedHashMap 保证 JSON 字段顺序稳定，方便前端调试时肉眼看
            Map<String, Object> banner = new LinkedHashMap<>(4);
            banner.put("id", item.getId());
            banner.put("title", item.getName());
            // 约定：字典项的 remark 字段存图片 URL（t_dict 没有专门的图片列）
            banner.put("imageUrl", item.getRemark());
            banner.put("link", null);
            result.add(banner);
        }
        return result;
    }

    @Override
    public List<ProductVO> recommended() {
        // ORDER BY RAND() 会全表扫描 + 排序，数据量大时很慢。
        // 演示库只有十几个商品，无所谓；上生产要换成"随机 id 范围查询"之类的方案。
        // 注意 LIMIT 的值来自常量而不是用户输入，不存在 SQL 注入。
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, ProductConst.STATUS_ON_SALE)
                        .last("ORDER BY RAND() LIMIT " + RECOMMEND_LIMIT));
        return products.stream().map(ProductVO::from).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> articles() {
        // 只查已发布的：待审核（0）和已拒绝（2）的文章不能出现在首页，
        // 否则审核功能形同虚设。
        List<Article> list = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, ARTICLE_PUBLISHED)
                        .orderByDesc(Article::getViewCount)
                        .last("LIMIT " + HOT_ARTICLE_LIMIT));

        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Article a : list) {
            Map<String, Object> m = new LinkedHashMap<>(6);
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("category", a.getCategory());
            m.put("coverUrl", a.getCoverUrl());
            m.put("viewCount", a.getViewCount());
            result.add(m);
        }
        return result;
    }

    @Override
    public Map<String, Object> stats() {
        // 需求里这就是 Mock 数据。不真去 count 的原因：
        // 一是首页每次都打三个 count 查询没必要，二是演示时数字好看比数字真实重要。
        Map<String, Object> stats = new LinkedHashMap<>(4);
        stats.put("productCount", 1280);
        stats.put("financingSuccess", 356);
        stats.put("userCount", 5420);
        return stats;
    }
}
