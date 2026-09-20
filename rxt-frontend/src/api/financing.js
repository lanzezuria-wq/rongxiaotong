import request from './request'

/**
 * M4 融资服务接口（契约路径 /api/financing/*，共 8 个，登录即可访问，
 * 角色校验在 Service 里按动作做：申请/发起小组只有农户能调）。
 */

/** 提交融资申请。返回 { id, matchedGroups[] }，matchedGroups 是同行业同省的可加入小组 */
export function applyFinancing(data) {
  return request.post('/financing/apply', data)
}

/** 我的融资申请列表。status 可选 */
export function listFinancing(params) {
  return request.get('/financing/list', { params })
}

/** 融资申请详情（含 description / group / approval） */
export function getFinancing(id) {
  return request.get(`/financing/${id}`)
}

/** 取消融资申请（会一并退出所在联保小组） */
export function cancelFinancing(id) {
  return request.delete(`/financing/apply/${id}`)
}

/** 可加入的联保小组列表。industry/province 可选，传了按它们强匹配 */
export function listGroups(params) {
  return request.get('/financing/groups', { params })
}

/** 联保小组详情（含成员名单） */
export function getGroup(id) {
  return request.get(`/financing/group/${id}`)
}

/** 申请加入联保小组。需同时传 groupId 和 applyId */
export function joinGroup(data) {
  return request.post('/financing/join', data)
}

/** 发起新联保小组。发起人必须已有一条「待匹配」的融资申请 */
export function createGroup(data) {
  return request.post('/financing/create-group', data)
}

export default {
  applyFinancing,
  listFinancing,
  getFinancing,
  cancelFinancing,
  listGroups,
  getGroup,
  joinGroup,
  createGroup
}
