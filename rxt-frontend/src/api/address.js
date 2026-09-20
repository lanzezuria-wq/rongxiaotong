import request from './request'

/**
 * M3 收货地址接口（契约路径 /api/address/*，共 4 个，全部要求登录）。
 *
 * <p>列表返回时默认地址排第一（后端已排好序），
 * 下单页的地址选择器可以直接把第一项当作默认选中项。
 */

/** 收货地址列表。每个地址含拼好的 fullAddress 字段 */
export function listAddress() {
  return request.get('/address/list')
}

/**
 * 新增收货地址。
 * @param {{receiver: string, phone: string, province?: string, city?: string,
 *          district?: string, detail: string, isDefault?: number}} data
 */
export function createAddress(data) {
  return request.post('/address', data)
}

/** 编辑收货地址（整体覆盖语义） */
export function updateAddress(id, data) {
  return request.put(`/address/${id}`, data)
}

/** 删除收货地址 */
export function deleteAddress(id) {
  return request.delete(`/address/${id}`)
}

export default { listAddress, createAddress, updateAddress, deleteAddress }
