import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { isLoggedIn, isAdmin, authState } from '@/utils/auth'

/**
 * 路由表 —— P1 的前端交付范围共 8 个页面（含修改密码）。
 *
 * <p>结构上用「嵌套路由」表达两种外壳：
 * <pre>
 *   /login  /register            ← 独立整屏页，无导航
 *   /user/*   → UserLayout   ← 顶部导航（个人中心、修改密码）
 *   /admin/*  → AdminLayout  ← 侧边菜单（4 个后台管理页）
 * </pre>
 * 布局组件里放 {@code <router-view />}，子页面就渲染在里面。
 * 这样切页时外壳不重新挂载，体验更顺，也少写重复的导航代码。
 *
 * <p>路由 meta 的两个约定：
 * <ul>
 *   <li>{@code public: true} —— 不需要登录（登录页、注册页）；</li>
 *   <li>{@code requiresAdmin: true} —— 需要管理员角色。</li>
 * </ul>
 * 它们是<b>前端的体验优化，不是安全边界</b>。真正的权限控制在后端
 * （SecurityConfig 的 URL 规则 + 方法上的 @PreAuthorize）——
 * 前端路由守卫拦不住直接构造 HTTP 请求的人。
 */
const routes = [
  { path: '/', redirect: '/login' },

  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, title: '注册' }
  },

  // ---------------- 个人中心（所有登录用户）----------------
  {
    path: '/user',
    component: () => import('@/layout/UserLayout.vue'),
    redirect: '/user/profile',
    children: [
      {
        path: 'profile',
        name: 'user-profile',
        component: () => import('@/views/user/ProfileView.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'password',
        name: 'user-password',
        component: () => import('@/views/user/PasswordView.vue'),
        meta: { title: '修改密码' }
      }
    ]
  },

  // ---------------- 后台管理（仅管理员）----------------
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/users',
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserListView.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'expert-audit',
        name: 'admin-expert-audit',
        component: () => import('@/views/admin/ExpertAuditView.vue'),
        meta: { title: '专家审核' }
      },
      {
        path: 'article-audit',
        name: 'admin-article-audit',
        component: () => import('@/views/admin/ArticleAuditView.vue'),
        meta: { title: '文章审核' }
      },
      {
        path: 'dict',
        name: 'admin-dict',
        component: () => import('@/views/admin/DictManageView.vue'),
        meta: { title: '数据字典' }
      }
    ]
  },

  // 兜底：匹配不到的路由统一回首页，避免出现白屏
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/** 登录后应该落哪一页：管理员进后台，其他人进个人中心 */
export function homePath() {
  return isAdmin() ? '/admin/users' : '/user/profile'
}

router.beforeEach((to) => {
  // 顺手改浏览器标签页标题
  document.title = to.meta.title ? `${to.meta.title} · 融销通` : '融销通 · 农业金融服务平台'

  // ---- 公开页面 ----
  if (to.meta.public) {
    // 已登录的人不该再看到登录/注册页，直接送回各自的首页
    if (isLoggedIn() && (to.name === 'login' || to.name === 'register')) {
      return homePath()
    }
    return true
  }

  // ---- 需要登录 ----
  if (!isLoggedIn()) {
    // 把目标地址记在 query 里，登录成功后跳回去（LoginView 里消费这个参数）
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // ---- 需要管理员 ----
  // 注意 meta 是逐层合并的，父路由 /admin 上写了 requiresAdmin，
  // 子路由 to.matched 里也会带上，这里直接读 to.meta 即可
  if (to.meta.requiresAdmin || to.matched.some((r) => r.meta?.requiresAdmin)) {
    if (!isAdmin()) {
      ElMessage.error('该页面仅平台管理员可访问')
      return homePath()
    }
  }

  return true
})

/**
 * 一个容易踩的坑：本地的 user 信息可能和 Token 不同源。
 *
 * 场景：用户 A 登录后，又被塞了一个用户 B 的 Token（比如手动改了 localStorage）。
 * 这时 role 判断就会错。这里做一次轻量自检：如果本地没有 user 信息
 * 却存在 Token，说明登录态是残缺的，直接清掉让它重新登录，
 * 避免出现"能进后台但点任何按钮都 403"的诡异状态。
 */
if (authState.token && !authState.user) {
  import('@/utils/auth').then(({ clearAuth }) => clearAuth())
}

export default router
