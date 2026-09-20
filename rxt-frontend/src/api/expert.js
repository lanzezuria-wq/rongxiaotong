import request from './request'

/**
 * M5 专家助力——专家接口（契约路径 /api/expert/*，共 2 个，游客可访问）。
 *
 * <p>⚠️ 返回的 id 是 t_expert.id（不是 t_user.id），
 * 专家详情、提问、预约接口传的都是这个 id。
 */

/** 已审核专家列表。specialty 是专业领域关键词（模糊匹配 domain） */
export function listExperts(params) {
  return request.get('/expert/list', { params })
}

/** 专家详情（含个人简介 intro + 最近发布的文章 recentArticles） */
export function getExpert(id) {
  return request.get(`/expert/${id}`)
}

export default { listExperts, getExpert }
