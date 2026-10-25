<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from './BrandMark.vue'
import { authState, clearAuth, isAdmin, isBank, isFarmer } from '@/utils/auth'
import { roleLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const open = ref(false)
const name = computed(() => authState.user?.name || '我的账户')
const links = [ ['/', '首页'], ['/products', '农产品集市'], ['/financing/my', '联保融资'], ['/experts', '农业专家'], ['/articles', '农业知识'] ]
watch(() => route.fullPath, () => { open.value = false })
function active(path) { return path === '/' ? route.path === '/' : route.path.startsWith(path.split('/').slice(0, 2).join('/')) }
function account(command) {
  if (command === 'logout') { clearAuth(); router.replace('/'); return }
  router.push(command)
}
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <BrandMark />
      <nav class="desktop-nav" aria-label="主导航">
        <router-link v-for="[path, label] in links" :key="path" :to="path" :class="{ active: active(path) }" :aria-current="active(path) ? 'page' : undefined">{{ label }}</router-link>
      </nav>
      <div class="header-actions">
        <router-link to="/cart" class="icon-button cart-link" aria-label="购物车"><el-icon :size="20"><ShoppingCart /></el-icon></router-link>
        <el-dropdown v-if="authState.token" @command="account" trigger="click">
          <button class="account-trigger"><span class="avatar-initial">{{ name.slice(0, 1) }}</span><span class="account-label">{{ name }}</span><el-icon><ArrowDown /></el-icon></button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item disabled>{{ roleLabel(authState.user?.role) }}</el-dropdown-item>
            <el-dropdown-item command="/user/profile">个人中心</el-dropdown-item>
            <el-dropdown-item command="/buyer/orders">我的订单</el-dropdown-item>
            <el-dropdown-item v-if="isFarmer()" command="/farmer/sales">我的销售</el-dropdown-item>
            <el-dropdown-item v-if="isFarmer()" command="/farmer/publish">发布商品</el-dropdown-item>
            <el-dropdown-item command="/financing/my">我的融资</el-dropdown-item>
            <el-dropdown-item command="/question/my">我的提问</el-dropdown-item>
            <el-dropdown-item v-if="isBank()" command="/bank/groups">银行审批</el-dropdown-item>
            <el-dropdown-item v-if="isAdmin()" command="/admin/users">管理工作台</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
        <template v-else><router-link to="/login" class="login-link">登录</router-link><router-link to="/register" class="button-link register-link">加入融销通</router-link></template>
        <button class="icon-button mobile-menu-button" aria-label="切换导航菜单" :aria-expanded="open" @click="open = !open"><el-icon :size="22"><Close v-if="open" /><Menu v-else /></el-icon></button>
      </div>
    </div>
    <nav v-if="open" class="mobile-nav" aria-label="手机导航"><router-link v-for="[path, label] in links" :key="path" :to="path" :class="{ active: active(path) }">{{ label }}</router-link></nav>
  </header>
</template>
