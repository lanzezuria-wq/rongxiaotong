import request from './request'

/**
 * M6 融资管理（银行端）接口（契约路径 /api/bank/*，共 3 个，仅 bank 角色）。
 */

/** 银行端联保小组列表（默认返回已成型的小组，含成员名单）。status 可选 */
export function listBankGroups(params) {
  return request.get('/bank/group/list', { params })
}

/**
 * 审批联保小组。
 * @param {{groupId: number, result: 'APPROVED'|'REJECTED', creditScore?: number,
 *          interestRate?: number, decisionAmount?: number, remark?: string}} data
 *   Mock 模式下 creditScore/interestRate/decisionAmount 可不传，后端自动生成
 */
export function approveGroup(data) {
  return request.put('/bank/approve', data)
}

/** 银行端统计看板（小组总数/待审批/已通过/已拒绝/核定额度合计） */
export function getBankStats() {
  return request.get('/bank/stats')
}

export default { listBankGroups, approveGroup, getBankStats }
