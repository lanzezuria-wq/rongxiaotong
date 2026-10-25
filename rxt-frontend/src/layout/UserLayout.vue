<script setup>
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'
</script>
<template>
  <div class="site-layout"><SiteHeader /><main id="main-content" class="account-layout container"><aside class="account-navigation"><span class="navigation-heading">我的账户</span><router-link to="/user/profile">个人资料</router-link><router-link to="/user/password">账户安全</router-link><router-link to="/buyer/orders">我的订单</router-link><router-link to="/financing/my">我的融资</router-link></aside><div class="account-content"><router-view /></div></main><SiteFooter /></div>
</template>
