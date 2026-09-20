import request from './request'

/**
 * M3 购物车接口（契约路径 /api/cart/*，共 4 个，全部要求登录）。
 */

/**
 * 加入购物车。
 * 同一商品 + 同一规格重复加购会累加数量，不会新增一行；数量不能超过库存。
 * @param {{productId: number, skuSpec?: string, quantity: number}} data
 */
export function addToCart(data) {
  return request.post('/cart', data)
}

/**
 * 购物车列表。
 * ⚠️ 商品被删除或下架时，该项的商品信息（productName/price/subtotal/stock）为 null，
 * 前端应提示用户移除，且不要让这类项参与结算。
 */
export function listCart() {
  return request.get('/cart/list')
}

/** 修改购物车数量。数量超过库存时后端会自动降到库存上限，不会报错 */
export function updateCartQuantity(id, quantity) {
  return request.put(`/cart/${id}`, { quantity })
}

/** 删除购物车项（只能删自己的） */
export function deleteCart(id) {
  return request.delete(`/cart/${id}`)
}

export default { addToCart, listCart, updateCartQuantity, deleteCart }
