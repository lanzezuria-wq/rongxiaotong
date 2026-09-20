<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { productCategoryLabel, formatPrice } from '@/utils/format'

/**
 * 商品卡片 —— 首页「热卖推荐」和商品列表页共用的展示单元。
 *
 * <p>设计上只负责展示和跳转，不含任何请求逻辑：数据由父组件拉好传入，
 * 点击卡片跳商品详情。这样首页和列表页都能复用，改样式只改这一处。
 */
const props = defineProps({
  /** 商品列表项（ProductVO）：id / name / coverImage / price / unit / salesCount / farmerName / categoryCode */
  product: { type: Object, required: true }
})

const router = useRouter()

/** 封面优先用后端拼好的 coverImage，退到 images 第一张；都没有则渲染占位 */
const cover = computed(() => props.product?.coverImage || props.product?.images?.[0] || '')

function goDetail() {
  router.push(`/products/${props.product.id}`)
}
</script>

<template>
  <el-card class="product-card" shadow="hover" :body-style="{ padding: 0 }" @click="goDetail">
    <el-image v-if="cover" :src="cover" fit="cover" class="product-cover">
      <template #error>
        <div class="cover-fallback"><el-icon :size="32"><Picture /></el-icon></div>
      </template>
    </el-image>
    <div v-else class="cover-fallback"><el-icon :size="32"><Picture /></el-icon></div>

    <div class="product-body">
      <div class="product-name">{{ product.name }}</div>

      <div class="price-row">
        <span class="price">¥<b>{{ formatPrice(product.price) }}</b></span>
        <span class="unit">/{{ product.unit }}</span>
      </div>

      <div class="meta-row">
        <span class="category">{{ productCategoryLabel(product.categoryCode) }}</span>
        <span class="sales">已售 {{ product.salesCount ?? 0 }}</span>
      </div>

      <div class="farmer">
        <el-icon :size="12"><User /></el-icon>
        <span>{{ product.farmerName || '农户' }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.product-card {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  transition: transform 0.15s ease;
}

.product-card:hover {
  transform: translateY(-3px);
}

.product-cover {
  width: 100%;
  height: 180px;
  display: block;
}

.cover-fallback {
  width: 100%;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
}

.product-body {
  padding: 12px;
}

.product-name {
  font-size: 14px;
  color: #1f2329;
  line-height: 1.4;
  min-height: 40px;
  /* 最多两行，超出省略 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
}

.price {
  color: #e64545;
  font-size: 12px;
}

.price b {
  font-size: 18px;
  font-weight: 600;
}

.unit {
  color: #8a9099;
  font-size: 12px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
}

.category {
  color: #6b7280;
}

.sales {
  color: #8a9099;
}

.farmer {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #8a9099;
}
</style>
