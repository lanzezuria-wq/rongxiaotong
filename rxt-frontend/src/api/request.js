import axios from 'axios'
import { ElMessage } from 'element-plus'
import { authState, clearAuth } from '@/utils/auth'

/**
 * axios 实例 —— 全项目唯一的 HTTP 出口。
 *
 * <p><b>为什么所有请求都必须走这里？</b>因为它承担了四件横切关注点，
 * 任何一处散落到业务组件里都会失控：
 * <ol>
 *   <li>统一带上 {@code Authorization: Bearer <token>}；</li>
 *   <li>把后端的 {@code {code, message, data}} 拆封，成功时直接返回 {@code data}；</li>
 *   <li>按错误码做全局提示（业务错误黑话翻译成人话）；</li>
 *   <li>{@code 1002/1006} 时清理登录态并跳登录页。</li>
 * </ol>
 *
 * <p><b>一个必须理解的背景：本项目后端「HTTP 状态码」和「业务错误码」是同时返回的。</b>
 * 比如密码错误会返回：
 * <pre>
 *   HTTP/1.1 401 Unauthorized
 *   {"code":1002, "message":"手机号或密码错误", "data":null}
 * </pre>
 * HTTP 状态码给浏览器/网关/监控用（401 就是"你没通过认证"），
 * 业务 code 给前端业务分流用（1002 是密码错、1006 是 Token 过期——都是 401，
 * 但前端处理方式完全不同：前者要停在登录页提示，后者可以静默刷新）。
 * 所以下面的拦截器要<b>两处都读</b>：axios 只把 2xx 当成功，4xx/5xx 会走 rejected 分支。
 */
const service = axios.create({
  // 只写 /api，不写完整域名 —— 由 Vite 的 proxy 转发到 8080（见 vite.config.js）。
  // 这样开发和生产（Nginx 反代）用同一份代码。
  baseURL: '/api',
  timeout: 15000
})

// ============================================================================
// 请求拦截器
// ============================================================================
service.interceptors.request.use(
  (config) => {
    if (authState.token) {
      config.headers.Authorization = `Bearer ${authState.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ============================================================================
// 响应拦截器
// ============================================================================

/**
 * 防止「登录过期」提示刷屏。
 *
 * 场景：后台管理页首屏会并发发 3~4 个请求，Token 一旦过期它们会同时失败，
 * 于是弹出 4 个一模一样的警告框，还会连续调用 4 次 router.replace。
 * 用一个时间戳节流：3 秒内只处理一次。
 */
let lastAuthErrorAt = 0

/** 跳登录页并清登录态。用动态 import 拿 router，避免 router ↔ api 的循环依赖 */
async function redirectToLogin(message) {
  const now = Date.now()
  if (now - lastAuthErrorAt < 3000) {
    return
  }
  lastAuthErrorAt = now

  clearAuth()
  ElMessage.warning(message)

  const { default: router } = await import('@/router')
  // 记下当前路径，登录成功后可跳回来（配合 LoginView 里的 redirect 处理）
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({
      path: '/login',
      query: current.fullPath === '/' ? {} : { redirect: current.fullPath }
    })
  }
}

service.interceptors.response.use(
  // ---------- HTTP 2xx：请求本身成功了，但不代表业务成功 ----------
  (response) => {
    // 文件流等非 JSON 响应直接放行（本项目暂无，但留着不会误伤）
    if (response.config.responseType === 'blob') {
      return response.data
    }

    const res = response.data

    // 防御：万一后端返回的不是标准结构（比如被网关换了页面），别让前端静默出错
    if (res === null || typeof res !== 'object' || typeof res.code === 'undefined') {
      return res
    }

    if (res.code === 0) {
      // 拆封：业务代码里直接拿到 data，不用每次写 res.data.data
      return res.data
    }

    // HTTP 200 但业务失败（少见，通常都是 4xx，但兜底）
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(res)
  },

  // ---------- HTTP 非 2xx：后端返回的仍是 Result 结构，按 business code 分流 ----------
  (error) => {
    // 情况一：压根没拿到响应（后端没起、网络断了、被代理拦了）
    if (!error.response) {
      const msg =
        error.code === 'ECONNABORTED'
          ? '请求超时，请检查后端服务是否正常运行'
          : '网络异常，无法连接后端服务（默认 http://127.0.0.1:8080）'
      ElMessage.error(msg)
      return Promise.reject(error)
    }

    const res = error.response.data
    const businessCode = res?.code

    switch (businessCode) {
      // 1002 认证失败：Token 缺失 / 无效 / 签名被篡改
      case 1002:
        redirectToLogin(res?.message || '登录状态无效，请重新登录')
        break

      // 1006 Token 已过期：与 1002 分开，因为这是完全正常的现象，文案要温和
      case 1006:
        redirectToLogin('登录已过期，请重新登录')
        break

      // 1007 权限不足：**不能跳登录页**！用户已登录，只是这个角色不能访问
      case 1007:
        ElMessage.error(res?.message || '没有权限执行该操作')
        break

      // 1005 被限流 / 账号锁定
      case 1005:
        ElMessage.warning(res?.message || '操作过于频繁，请稍后再试')
        break

      // 其他业务错误（1001 参数、1003 账号状态、1004 验证码、1008 资源不存在……）
      default:
        ElMessage.error(res?.message || `请求失败（HTTP ${error.response.status}）`)
    }

    return Promise.reject(res ?? error)
  }
)

export default service
