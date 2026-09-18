import request from './request'

/**
 * M7 系统管理 + 文章审核接口（SRS 表38-41 / 表58-59，共 8 个）。
 *
 * <p><b>注意最后两个的路径不在 /api/admin 下。</b>
 * 「文章审核」按 SRS 表58 归在专家助力模块，路径是 {@code PUT /api/article/{id}/audit}；
 * 为了给审核页取数，连 {@code GET /api/article/list} 一起实现。
 * 这两条需要后端在 SecurityConfig 里单独配权限规则——前端只要路径写对就行。
 *
 * <p>权限分层（容易记混，这里写清楚）：
 * <ul>
 *   <li>{@code GET  /admin/dict/{type}} —— <b>所有登录用户</b>都能调，因为前端下拉框要用；</li>
 *   <li>{@code POST/PUT/DELETE /admin/dict/*} —— 仅管理员；</li>
 *   <li>其余全部 —— 仅管理员。</li>
 * </ul>
 */

// ==================== M7-1 用户管理 ====================

/**
 * 用户列表（分页 + 筛选）。
 * 专家审核页也复用它取待审核专家：{ role: 'expert', status: 0 }。
 */
export function listUsers(params) {
  return request.get('/admin/user/list', { params })
}

/**
 * 启用 / 禁用用户。status 只能传 1（正常）或 3（已禁用）。
 * 操作人 id 由后端从 Token 取，前端不能伪造。
 */
export function changeUserStatus(id, status) {
  return request.put(`/admin/user/${id}/status`, { status })
}

// ==================== M7-2 专家审核 ====================

/**
 * 审核专家账号。
 * ⚠️ 路径里的 id 是 <b>t_user.id</b>，不是 t_expert.id ——
 * 待审核状态下 t_expert 里还没有数据（它是审核通过后才写入的）。
 * 这个 id 直接取用户列表接口返回的 records[i].id。
 *
 * @param {number} id 用户 id
 * @param {{status: 'APPROVED'|'REJECTED', remark?: string}} data 拒绝时 remark 必填
 */
export function auditExpert(id, data) {
  return request.put(`/admin/expert/${id}/audit`, data)
}

// ==================== M7-3 数据字典 ====================

/** 按类型查字典项。所有登录用户可用 */
export function listDictByType(type) {
  return request.get(`/admin/dict/${type}`)
}

/** 新增字典项（仅管理员）。同类型下 code 不能重复 */
export function createDict(data) {
  return request.post('/admin/dict', data)
}

/** 编辑字典项（仅管理员） */
export function updateDict(id, data) {
  return request.put(`/admin/dict/${id}`, data)
}

/** 删除字典项（仅管理员） */
export function deleteDict(id) {
  return request.delete(`/admin/dict/${id}`)
}

// ==================== M7-8 文章审核（表58，路径不在 /api/admin 下）====================

/** 文章列表（分页 + 筛选）。审核页传 status=0 取待审核 */
export function listArticles(params) {
  return request.get('/article/list', { params })
}

/** 审核文章（仅管理员）。拒绝时 remark 必填 */
export function auditArticle(id, data) {
  return request.put(`/article/${id}/audit`, data)
}

export default {
  listUsers,
  changeUserStatus,
  auditExpert,
  listDictByType,
  createDict,
  updateDict,
  deleteDict,
  listArticles,
  auditArticle
}
