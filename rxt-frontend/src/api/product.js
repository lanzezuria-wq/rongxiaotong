import request from './request'

/**
 * M3 商品接口（契约路径 /api/product/*，共 6 个）。
 *
 * <p>商品浏览对游客开放（GET /api/product/** 在 SecurityConfig 放行），
 * 发布 / 编辑 / 上下架 / 删除要求农户角色。
 * 本阶段（商品浏览链路）只封装浏览用的 list / detail 两个，
 * 其余四个在"农户发布 / 我的商品"阶段再接。
 */

/**
 * 商品列表（分页 + 关键词 + 分类 + 排序）。
 * @param {object} params
 *   keyword      商品名模糊匹配
 *   categoryCode 分类编码（t_dict.type=product_category 的 code）
 *   sort         排序：default 综合 / price_asc / price_desc / sales 销量 / newest 最新
 *   page / size  分页，size 后端有 100 的上限
 * @returns PageResult：{ records, total, page, size, pages }
 */
export function listProducts(params) {
  return request.get('/product/list', { params })
}

/**
 * 商品详情。后端会顺带把浏览量 +1。
 * 返回 ProductDetailVO —— 除列表字段外还有：
 * description / stock / viewCount / skuList [{ spec, price, stock }]
 */
export function getProduct(id) {
  return request.get(`/product/${id}`)
}

export default { listProducts, getProduct }
