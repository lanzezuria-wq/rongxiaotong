<script setup>
defineProps({ compact: Boolean, inverse: Boolean })
</script>

<template>
  <router-link to="/" class="brand-lockup" :class="{ 'brand-inverse': inverse }" aria-label="融销通首页">
    <svg class="brand-symbol" viewBox="0 0 40 40" fill="none" aria-hidden="true">
      <path d="M20 34V12M20 22C11 22 7 17 7 10c8 0 13 4 13 12Zm0-6C20 8 25 4 33 4c0 8-5 12-13 12Z" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" />
      <path d="M7 30c8-5 18-5 26 0M5 36c10-5 20-5 30 0" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
    </svg>
    <span class="brand-wordmark">融销通<span v-if="!compact" class="brand-caption">农业融销服务平台</span></span>
  </router-link>
</template>
