import request from './request'

/**
 * M3 商品接口（契约路径 /api/product/*，共 6 个）。
 *
 * <p>商品浏览对游客开放（GET /api/product/** 在 SecurityConfig 放行），
 * 发布 / 编辑 / 上下架 / 删除要求农户角色。
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

/**
 * 发布商品（仅农户）。
 * @param {object} data
 *   name 商品名(≤50) / categoryCode 分类编码 / price 单价 / stock 库存 /
 *   unit 单位(≤10) / description 描述 / images 图片URL数组(1~6) /
 *   skuList [{ spec, price, stock }] 规格，无规格可不传
 */
export function publishProduct(data) {
  return request.post('/product', data)
}

export default { listProducts, getProduct, publishProduct }
