<script setup>
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import BusinessNavigation from '@/components/BusinessNavigation.vue'
</script>
<template>
  <div class="site-layout"><SiteHeader /><BusinessNavigation /><main id="main-content" class="site-main"><router-view /></main><SiteFooter /></div>
</template>
