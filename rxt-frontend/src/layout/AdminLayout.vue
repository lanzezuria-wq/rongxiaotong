<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '@/components/BrandMark.vue'
import { authState, clearAuth } from '@/utils/auth'
const route = useRoute()
const router = useRouter()
const open = ref(false)
const name = computed(() => authState.user?.name || '平台管理员')
const links = [ ['/admin/users', '用户管理', 'User'], ['/admin/expert-audit', '专家审核', 'Stamp'], ['/admin/article-audit', '文章审核', 'DocumentChecked'], ['/admin/dict', '数据字典', 'Collection'], ['/admin/logs', '操作日志', 'List'] ]
watch(() => route.path, () => { open.value = false })
function logout() { clearAuth(); router.replace('/login') }
</script>
<template>
  <div class="workspace-shell">
    <header class="workspace-mobile-header"><BrandMark compact /><button class="icon-button" aria-label="切换管理菜单" :aria-expanded="open" @click="open = !open"><el-icon :size="22"><Close v-if="open" /><Menu v-else /></el-icon></button></header>
    <aside class="workspace-sidebar" :class="{ 'is-open': open }">
      <BrandMark /><div class="workspace-scope"><span class="scope-indicator" />平台管理工作台</div>
      <nav aria-label="管理导航"><router-link v-for="[path, label, icon] in links" :key="path" :to="path" :aria-current="route.path === path ? 'page' : undefined"><el-icon><component :is="icon" /></el-icon><span>{{ label }}</span><el-icon v-if="route.path === path" class="nav-arrow"><ArrowRight /></el-icon></router-link></nav>
      <div class="sidebar-bottom"><router-link to="/">返回平台首页<el-icon><TopRight /></el-icon></router-link><div class="sidebar-account"><span class="avatar-initial">{{ name.slice(0, 1) }}</span><div><strong>{{ name }}</strong><small>平台管理员</small></div><button class="icon-button" aria-label="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></button></div></div>
    </aside>
    <div v-if="open" class="sidebar-backdrop" @click="open = false" />
    <main id="main-content" class="workspace-content"><div class="workspace-breadcrumb"><router-link to="/admin/users">管理工作台</router-link><span>/</span><span>{{ route.meta.title }}</span><span class="workspace-status"><span class="scope-indicator" />管理业务</span></div><router-view /></main>
  </div>
</template>
