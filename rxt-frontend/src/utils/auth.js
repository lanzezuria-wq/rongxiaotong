import { reactive } from 'vue'

/**
 * 登录态存储 —— 用最朴素的方式实现，没有引入 Pinia。
 *
 * <p>为什么不上 Pinia？本项目只有「当前登录用户」这一份需要跨组件共享的状态，
 * 用一个 reactive 对象就够了。引入状态管理库会多一层依赖和一套心智模型，
 * 收益抵不上成本。等到状态种类变多（比如购物车、多标签页缓存）再上 Pinia 不迟。
 *
 * <p>持久化用 localStorage，因为它「关掉浏览器再打开还在」。
 * 更严谨的方案是 httpOnly Cookie（JS 读不到，能防 XSS 偷 Token），
 * 但那需要后端配合改写 Set-Cookie，且要处理 CSRF，超出 P1 范围。
 */

const TOKEN_KEY = 'rxt_token'
const USER_KEY = 'rxt_user'

/** 反序列化时容错：localStorage 里可能被人手改坏，或存的是旧结构 */
function readStoredUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

/**
 * 全局登录态。组件里 import 进来直接读即可，它是响应式的，
 * 登录成功赋值后，所有用到它的模板会自动重新渲染。
 */
export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readStoredUser()
})

/** 登录 / 注册成功后调用 */
export function setAuth(token, user) {
  authState.token = token || ''
  authState.user = user || null

  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

/** 只更新用户信息（改完个人资料后刷新顶部显示） */
export function setUser(user) {
  authState.user = user || null
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
}

/** 退出登录 / Token 失效时调用 */
export function clearAuth() {
  setAuth('', null)
}

export function isLoggedIn() {
  return !!authState.token
}

export function isAdmin() {
  return authState.user?.role === 'admin'
}

/** 取当前用户 id（后端大部分接口从 Token 里解，前端只在少数地方需要比对） */
export function currentUserId() {
  return authState.user?.id ?? null
}
