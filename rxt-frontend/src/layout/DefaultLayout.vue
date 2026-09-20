<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authState, clearAuth, isLoggedIn, isAdmin, isFarmer, isBank } from '@/utils/auth'
import { roleLabel } from '@/utils/format'

/**
 * 公共外壳：顶部导航 + 内容区 + 页脚。
 *
 * <p>这是面向「游客也能看」的门户外壳，和 UserLayout（登录后的个人中心外壳）
 * 的区别是：未登录时右侧显示「登录 / 注册」按钮，而不是强制要求登录。
 * 首页、商品列表、商品详情（以及后续的文章、专家页）都挂在它下面。
 */
const route = useRoute()
const router = useRouter()

/** 子路由（详情页等）也要让对应的一级菜单保持高亮 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/products')) return '/products'
  if (route.path.startsWith('/articles')) return '/articles'
  if (route.path.startsWith('/experts')) return '/experts'
  if (route.path.startsWith('/financing')) return '/financing/my'
  return route.path
})

const displayName = computed(() => authState.user?.name || authState.user?.phone || '')
const displayRole = computed(() => roleLabel(authState.user?.role))

const avatarText = computed(() => {
  const n = authState.user?.name
  if (n) return n.slice(0, 1)
  return authState.user?.phone?.slice(3, 4) || 'U'
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.replace('/')
}

function onCommand(cmd) {
  if (cmd === 'profile') router.push('/user/profile')
  else if (cmd === 'orders') router.push('/buyer/orders')
  else if (cmd === 'financing') router.push('/financing/my')
  else if (cmd === 'questions') router.push('/question/my')
  else if (cmd === 'bank') router.push('/bank/groups')
  else if (cmd === 'sales') router.push('/farmer/sales')
  else if (cmd === 'publish') router.push('/farmer/publish')
  else if (cmd === 'admin') router.push('/admin/users')
  else if (cmd === 'logout') handleLogout()
}
</script>

<template>
  <el-container class="default-layout">
    <el-header class="top-nav">
      <div class="brand" @click="router.push('/')">
        <span class="brand-dot" />
        <span class="brand-name">融销通</span>
        <span class="brand-sub">农业金融服务平台</span>
      </div>

      <el-menu :default-active="activeMenu" mode="horizontal" class="nav-menu" router :ellipsis="false">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/products">农产品</el-menu-item>
        <el-menu-item index="/articles">文章</el-menu-item>
        <el-menu-item index="/experts">专家</el-menu-item>
        <el-menu-item index="/financing/my">融资服务</el-menu-item>
      </el-menu>

      <div class="nav-right">
        <el-button text class="cart-btn" title="购物车" @click="router.push('/cart')">
          <el-icon :size="20"><ShoppingCart /></el-icon>
        </el-button>

        <template v-if="isLoggedIn()">
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ displayName }}</span>
              <el-tag size="small" type="info" effect="plain">{{ displayRole }}</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                <el-dropdown-item command="financing">我的融资</el-dropdown-item>
                <el-dropdown-item command="questions">我的提问</el-dropdown-item>
                <el-dropdown-item v-if="isFarmer()" command="sales">我的销售</el-dropdown-item>
                <el-dropdown-item v-if="isFarmer()" command="publish">发布商品</el-dropdown-item>
                <el-dropdown-item v-if="isBank()" command="bank">银行审批</el-dropdown-item>
                <el-dropdown-item v-if="isAdmin()" command="admin">后台管理</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button text @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>

    <el-main class="layout-main">
      <router-view />
    </el-main>

    <el-footer class="layout-footer">
      融销通 · 农业金融服务平台 —— 课程项目演示
    </el-footer>
  </el-container>
</template>

<style scoped>
.default-layout {
  min-height: 100vh;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  cursor: pointer;
  white-space: nowrap;
}

.brand-dot {
  width: 10px;
  height: 10px;
  background: #16a34a;
  border-radius: 50%;
}

.brand-name {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.brand-sub {
  font-size: 12px;
  color: #8a9099;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cart-btn {
  padding: 8px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 6px;
  outline: none;
}

.user-chip:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: #16a34a;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: #1f2329;
}

.layout-main {
  padding: 0;
  background: #f5f7fa;
}

.layout-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  color: #8a9099;
  font-size: 12px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
</style>
