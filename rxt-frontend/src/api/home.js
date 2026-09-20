import request from './request'

/**
 * M2 首页接口（契约路径 /api/home/*，共 4 个，全部游客可访问）。
 *
 * <p>这 4 个接口都在 SecurityConfig 里放行了（GET /api/home/**），
 * 所以未登录用户打开首页就能看到内容，不依赖登录态。
 * 组件里直接调用即可，不需要"先判断登录"的前置处理。
 */

/** 轮播图。返回 [{ id, title, imageUrl, link }]，link 目前恒为 null */
export function getBanners() {
  return request.get('/home/banners')
}

/**
 * 推荐商品：在售商品里随机 12 个。
 * 返回完整的 ProductVO 数组（含 coverImage / price / unit / salesCount / farmerName…），
 * 可以直接复用商品卡片组件渲染。
 */
export function getRecommended() {
  return request.get('/home/recommended')
}

/**
 * 热门文章 Top 5（按浏览量倒序，只含已发布）。
 * 注意返回的是 Map 精简结构，不是完整 ArticleVO：
 * [{ id, title, category, coverUrl, viewCount }]
 */
export function getHomeArticles() {
  return request.get('/home/articles')
}

/**
 * 首页统计数字（Mock，不是真实统计）。
 * 返回 { productCount, financingSuccess, userCount }，字段名以后端 HomeServiceImpl 为准。
 */
export function getStats() {
  return request.get('/home/stats')
}

export default { getBanners, getRecommended, getHomeArticles, getStats }
