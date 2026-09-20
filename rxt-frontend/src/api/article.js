import request from './request'

/**
 * M5 专家助力——文章接口（契约路径 /api/article/*）。
 *
 * <p>列表和详情对游客开放；发布/点赞/评论要求登录。
 * 注意：文章列表的 status 参数用于筛选（1=已发布），公开页应传 status=1。
 */

/**
 * 文章列表。
 * @param {{page?: number, size?: number, status?: number, keyword?: string}} params
 *   公开页传 status=1 只看已发布；审核页（admin.js）传 status=0 看待审核
 */
export function listArticles(params) {
  return request.get('/article/list', { params })
}

/** 文章详情（含正文、标签数组、点赞/评论数、当前用户是否已点赞、评论列表） */
export function getArticle(id) {
  return request.get(`/article/${id}`)
}

/** 点赞 / 取消点赞（可切换）。返回 { isLiked, likeCount }，直接覆盖本地显示 */
export function likeArticle(id) {
  return request.put(`/article/${id}/like`)
}

/** 发表评论（≤200 字） */
export function commentArticle(id, content) {
  return request.post(`/article/${id}/comment`, { content })
}

/**
 * 发布文章。农户 → 待审核；专家 → 直接发布（状态由角色决定，前端不能传）。
 * @param {{title: string, category: string, content: string, coverUrl?: string, tags?: string[]}} data
 */
export function publishArticle(data) {
  return request.post('/article', data)
}

export default { listArticles, getArticle, likeArticle, commentArticle, publishArticle }
