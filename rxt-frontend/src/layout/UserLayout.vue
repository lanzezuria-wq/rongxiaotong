<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authState, clearAuth, isAdmin } from '@/utils/auth'
import { roleLabel } from '@/utils/format'

/**
 * 普通登录用户的外壳：顶部一条导航。
 *
 * <p>管理员的页面用的是另一套外壳（AdminLayout 的左侧菜单），
 * 因为后台管理页多、层级深，用侧边栏更合适。
 */
const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

const displayName = computed(() => authState.user?.name || authState.user?.phone || '未登录')
const displayRole = computed(() => roleLabel(authState.user?.role))

/** 头像占位字母：有名字取首字，否则取手机号第 4 位 */
const avatarText = computed(() => {
  const n = authState.user?.name
  if (n) return n.slice(0, 1)
  return authState.user?.phone?.slice(3, 4) || 'U'
})

function go(path) {
  router.push(path)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户点了取消
  }
  clearAuth()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    handleLogout()
  } else if (cmd === 'admin') {
    router.push('/admin/users')
  }
}
</script>

<template>
  <el-container class="user-layout">
    <el-header class="top-nav">
      <div class="brand" @click="go('/user/profile')">
        <span class="brand-dot" />
        <span class="brand-name">融销通</span>
        <span class="brand-sub">农业金融服务平台</span>
      </div>

      <el-menu :default-active="activeMenu" mode="horizontal" class="nav-menu" router :ellipsis="false">
        <el-menu-item index="/user/profile">个人中心</el-menu-item>
        <el-menu-item index="/user/password">修改密码</el-menu-item>
      </el-menu>

      <div class="nav-right">
        <el-button v-if="isAdmin()" text type="primary" @click="go('/admin/users')">
          进入后台管理
        </el-button>

        <el-dropdown @command="onCommand">
          <span class="user-chip">
            <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-tag size="small" type="info" effect="plain">{{ displayRole }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="admin" v-if="isAdmin()">后台管理</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.user-layout {
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
</style>
