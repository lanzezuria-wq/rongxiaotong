<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { isBank, isFarmer } from '@/utils/auth'
const route = useRoute()
const section = computed(() => {
  const path = route.path
  if (path.startsWith('/bank/')) return { title: '银行工作台', links: [['/bank/groups', '联保小组审批'], ['/bank/stats', '融资统计']] }
  if (path.startsWith('/financing/')) return { title: '联保融资', links: [['/financing/my', '我的融资'], ['/financing/groups', '联保小组'], ['/financing/apply', '融资申请']] }
  if (path.startsWith('/question/')) return { title: '专家助力', links: [['/experts', '农业专家'], ['/question/my', '我的提问'], ['/question/ask', '在线提问']] }
  if (path.startsWith('/farmer/')) return { title: '农户工作台', links: isFarmer() ? [['/farmer/sales', '我的销售'], ['/farmer/publish', '发布商品']] : [] }
  if (path === '/cart' || path.startsWith('/buyer/') || path.startsWith('/order/')) return { title: '交易中心', links: [['/buyer/orders', '我的订单'], ['/cart', '购物车']] }
  return null
})
</script>
<template>
  <div v-if="section" class="business-navigation">
    <div class="container business-navigation-inner">
      <span class="business-scope">{{ section.title }}</span>
      <nav :aria-label="section.title">
        <router-link v-for="[path, label] in section.links" :key="path" :to="path" :aria-current="route.path === path ? 'page' : undefined">{{ label }}</router-link>
      </nav>
      <router-link v-if="isBank() && !route.path.startsWith('/bank/')" to="/bank/groups" class="business-bank-link">银行审批<el-icon><TopRight /></el-icon></router-link>
    </div>
  </div>
</template>
