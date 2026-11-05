<script setup>
import { computed } from 'vue'
import { productCategoryLabel, formatPrice } from '@/utils/format'
import { productImage } from '@/utils/images'
const props = defineProps({ product: { type: Object, required: true } })
const cover = computed(() => productImage(props.product.coverImage || props.product.images?.[0]))
</script>
<template>
  <router-link :to="`/products/${product.id}`" class="product-card" :aria-label="`查看${product.name}`">
    <el-image v-if="cover" :src="cover" :alt="product.name" fit="cover" class="product-cover" loading="lazy"><template #error><div class="cover-fallback"><el-icon :size="30"><Picture /></el-icon><span>图片暂不可用</span></div></template></el-image>
    <div v-else class="cover-fallback"><el-icon :size="30"><Picture /></el-icon><span>{{ productCategoryLabel(product.categoryCode) }} · 暂无图片</span></div>
    <div class="product-body"><h3 class="product-name">{{ product.name }}</h3><div class="price-row"><span class="price">¥<b>{{ formatPrice(product.price) }}</b></span><span class="unit">/ {{ product.unit }}</span></div><div class="meta-row"><span>{{ productCategoryLabel(product.categoryCode) }}</span><span>已售 {{ product.salesCount ?? 0 }}</span></div><div class="farmer"><el-icon><User /></el-icon><span>{{ product.farmerName || '农户名称未提供' }}</span></div></div>
  </router-link>
</template>
