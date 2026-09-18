<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authState, clearAuth } from '@/utils/auth'
import { roleLabel } from '@/utils/format'

/**
 * 后台管理外壳：左侧菜单 + 顶部面包屑。
 *
 * <p>菜单项就是 P1 要交付的 4 个管理页面。
 * 这里用 {@code el-menu} 的 {@code router} 模式 —— 点菜单直接按 index 当路径跳转，
 * 不用自己写 click 处理函数。
 */
const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

const breadcrumb = computed(() => {
  const title = route.meta?.title || ''
  return ['后台管理', title].filter(Boolean)
})

const displayName = computed(() => authState.user?.name || authState.user?.phone || '未登录')
const displayRole = computed(() => roleLabel(authState.user?.role))

const avatarText = computed(() => {
  const n = authState.user?.name
  if (n) return n.slice(0, 1)
  return authState.user?.phone?.slice(3, 4) || 'A'
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
  router.replace('/login')
}

function onCommand(cmd) {
  if (cmd === 'logout') handleLogout()
  else if (cmd === 'home') router.push('/user/profile')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="210px" class="admin-aside">
      <div class="aside-brand">
        <span class="brand-dot" />
        <div>
          <div class="brand-name">融销通</div>
          <div class="brand-sub">后台管理</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" class="admin-menu" router>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/expert-audit">
          <el-icon><Stamp /></el-icon>
          <span>专家审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/article-audit">
          <el-icon><DocumentChecked /></el-icon>
          <span>文章审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/dict">
          <el-icon><Collection /></el-icon>
          <span>数据字典</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(item, i) in breadcrumb" :key="i">{{ item }}</el-breadcrumb-item>
        </el-breadcrumb>

        <el-dropdown @command="onCommand">
          <span class="user-chip">
            <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-tag size="small" type="danger" effect="plain">{{ displayRole }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">返回个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
}

.admin-aside {
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.aside-brand {
  display: flex;
  gap: 10px;
  align-items: center;
  height: 60px;
  padding: 0 18px;
  border-bottom: 1px solid #f0f2f5;
}

.brand-dot {
  width: 10px;
  height: 10px;
  background: #16a34a;
  border-radius: 50%;
  flex-shrink: 0;
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.brand-sub {
  font-size: 11px;
  color: #8a9099;
}

.admin-menu {
  border-right: none;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
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
  background: #dc2626;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: #1f2329;
}

.admin-main {
  padding: 0;
  overflow-y: auto;
  background: #f5f7fa;
}
</style>
