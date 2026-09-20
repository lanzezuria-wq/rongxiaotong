import request from './request'

/**
 * M3 订单接口（契约路径 /api/order/*，共 7 个，全部要求登录）。
 *
 * <p>状态机：0 待付款 → 1 待发货 → 2 已发货 → 3 已完成，0 可取消 → 4 已取消。
 * 前端只需要按当前状态显示对应操作按钮，状态的合法性由后端二次校验。
 */

/**
 * 创建订单（从购物车结算）。
 * @param {{cartIds: number[], addressId: number}} data
 */
export function createOrder(data) {
  return request.post('/order/create', data)
}

/**
 * 订单列表。
 * @param {{role: 'buyer'|'farmer', status?: number, page?: number, size?: number}} params
 *   role 必传：buyer 查我买的 / farmer 查我卖的
 */
export function listOrders(params) {
  return request.get('/order/list', { params })
}

/** 订单详情（含商品明细 items 和拼好的收货信息 address 字符串） */
export function getOrder(id) {
  return request.get(`/order/${id}`)
}

/** Mock 支付：待付款 → 待发货 */
export function payOrder(orderId) {
  return request.post('/order/pay', { orderId })
}

/** 卖家发货：待发货 → 已发货。需填快递公司和单号 */
export function shipOrder(id, data) {
  return request.put(`/order/${id}/ship`, data)
}

/** 买家确认收货：已发货 → 已完成（同时累计销量） */
export function receiveOrder(id) {
  return request.put(`/order/${id}/receive`)
}

/** 取消订单：仅待付款可取消（后端会回滚库存） */
export function cancelOrder(id) {
  return request.put(`/order/${id}/cancel`)
}

export default {
  createOrder,
  listOrders,
  getOrder,
  payOrder,
  shipOrder,
  receiveOrder,
  cancelOrder
}
