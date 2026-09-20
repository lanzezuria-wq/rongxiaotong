import request from './request'

/**
 * M5 专家助力——在线提问接口（契约路径 /api/question/*，共 3 个，登录即可访问）。
 */

/**
 * 提问。
 * @param {{expertId?: number, title: string, content: string}} data
 *   expertId 可选（t_expert.id）：填了是定向提问，不填进公共池任何专家可答
 */
export function askQuestion(data) {
  return request.post('/question', data)
}

/**
 * 提问列表。按角色返回不同视角：
 * 普通用户看「我提的问题」，专家看「点名问我的 + 公开 + 我提的」。
 * status 可选（0 待回答 / 1 已回答）
 */
export function listQuestions(params) {
  return request.get('/question/list', { params })
}

/** 专家回答（仅专家，只能回答一次） */
export function answerQuestion(id, answer) {
  return request.put(`/question/${id}/answer`, { answer })
}

export default { askQuestion, listQuestions, answerQuestion }
