import request from './request'

/**
 * M3 购物车接口（契约路径 /api/cart/*，共 4 个，全部要求登录）。
 *
 * <p>本阶段只封装"加入购物车"一个 —— 商品详情页的加购动作要用；
 * 列表 / 改数量 / 删除在购物车页（交易闭环阶段）再接。
 */

/**
 * 加入购物车。
 * 同一商品 + 同一规格重复加购会累加数量，不会新增一行；数量不能超过库存。
 * @param {{productId: number, skuSpec?: string, quantity: number}} data
 *   skuSpec 无规格商品不传
 */
export function addToCart(data) {
  return request.post('/cart', data)
}

export default { addToCart }
