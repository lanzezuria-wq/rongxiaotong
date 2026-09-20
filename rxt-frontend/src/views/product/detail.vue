<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProduct } from '@/api/product'
import { addToCart } from '@/api/cart'
import { isLoggedIn } from '@/utils/auth'
import { formatPrice, productCategoryLabel } from '@/utils/format'

/**
 * 商品详情页 —— 图片 + SKU 选择 + 加购（游客可浏览，加购需登录）。
 *
 * <p>规格的处理逻辑：
 * <ul>
 *   <li>有 skuList 时默认选中第一个规格，单价 / 库存都跟着选中的规格走；</li>
 *   <li>无规格商品（skuList 为空）直接用商品的基础价和总库存。</li>
 * </ul>
 * 加购前先本地判断登录态，给用户明确的跳转，而不是等后端 401 再跳。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const adding = ref(false)
const product = ref(null)

const selectedSpec = ref(null)
const quantity = ref(1)
const activeImage = ref(0)

const images = computed(() => {
  const imgs = product.value?.images || []
  if (imgs.length) return imgs
  return product.value?.coverImage ? [product.value.coverImage] : []
})

/** 当前选中的规格项；无规格时为 null */
const selectedSku = computed(() => {
  const list = product.value?.skuList || []
  if (!list.length) return null
  return list.find((s) => s.spec === selectedSpec.value) || list[0]
})

const currentPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
const currentStock = computed(() => selectedSku.value?.stock ?? product.value?.stock ?? 0)

// 切换规格后数量重置为 1，避免带过来一个超过新规格库存的数字
watch(selectedSku, () => {
  quantity.value = 1
})

async function load() {
  loading.value = true
  try {
    const data = await getProduct(route.params.id)
    product.value = data
    if (data?.skuList?.length) selectedSpec.value = data.skuList[0].spec
    document.title = `${data?.name || '商品详情'} · 融销通`
  } catch (e) {
    // 拦截器已统一提示（1008 商品不存在等）
  } finally {
    loading.value = false
  }
}

async function handleAddToCart() {
  if (!isLoggedIn()) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!currentStock.value) {
    ElMessage.warning('该商品暂时无货')
    return
  }
  adding.value = true
  try {
    await addToCart({
      productId: product.value.id,
      skuSpec: selectedSku.value?.spec ?? null,
      quantity: quantity.value
    })
    ElMessage.success('已加入购物车')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    adding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <template v-if="product">
      <el-card shadow="never" class="detail-main">
        <div class="detail-top">
          <!-- 左：图片 -->
          <div class="gallery">
            <el-image v-if="images[activeImage]" :src="images[activeImage]" fit="cover" class="main-image">
              <template #error>
                <div class="img-fallback"><el-icon :size="40"><Picture /></el-icon></div>
              </template>
            </el-image>
            <div v-else class="img-fallback"><el-icon :size="40"><Picture /></el-icon></div>

            <div v-if="images.length > 1" class="thumbs">
              <el-image
                v-for="(img, i) in images"
                :key="i"
                :src="img"
                fit="cover"
                class="thumb"
                :class="{ active: i === activeImage }"
                @click="activeImage = i"
              />
            </div>
          </div>

          <!-- 右：购买信息 -->
          <div class="info">
            <div class="name">{{ product.name }}</div>
            <div class="sub-row">
              <span class="category">{{ productCategoryLabel(product.categoryCode) }}</span>
              <span class="meta">已售 {{ product.salesCount ?? 0 }}</span>
              <span class="meta">浏览 {{ product.viewCount ?? 0 }}</span>
            </div>

            <div class="price-block">
              <span class="price">¥<b>{{ formatPrice(currentPrice) }}</b></span>
              <span class="unit">/ {{ product.unit }}</span>
            </div>

            <div v-if="product.skuList?.length" class="sku-block">
              <div class="block-label">规格</div>
              <el-radio-group v-model="selectedSpec">
                <el-radio-button v-for="s in product.skuList" :key="s.spec" :value="s.spec">
                  {{ s.spec }}
                </el-radio-button>
              </el-radio-group>
            </div>

            <div class="qty-block">
              <div class="block-label">数量</div>
              <el-input-number
                v-model="quantity"
                :min="1"
                :max="Math.min(999, currentStock || 1)"
                :disabled="!currentStock"
              />
              <span class="stock-tip">库存 {{ currentStock }} 件</span>
            </div>

            <div class="action-row">
              <el-button type="primary" size="large" :loading="adding" @click="handleAddToCart">
                <el-icon><ShoppingCart /></el-icon>
                <span style="margin-left: 4px">加入购物车</span>
              </el-button>
            </div>

            <div class="farmer-block">
              <el-icon><User /></el-icon>
              <span>农户：{{ product.farmerName || '匿名农户' }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="detail-desc">
        <template #header><span class="card-title">商品详情</span></template>
        <div class="desc-text">{{ product.description || '暂无描述' }}</div>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在或已下架">
      <el-button type="primary" @click="$router.push('/products')">返回商品列表</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 60vh;
  padding: 20px 0 32px;
}

.detail-main,
.detail-desc {
  max-width: 1200px;
  margin: 0 auto 16px;
}

.detail-top {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 32px;
}

/* ---------- 图片 ---------- */
.gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.main-image {
  width: 100%;
  height: 360px;
  display: block;
  border-radius: 8px;
}

.img-fallback {
  width: 100%;
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
  border-radius: 8px;
}

.thumbs {
  display: flex;
  gap: 8px;
}

.thumb {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  cursor: pointer;
  border: 2px solid transparent;
}

.thumb.active {
  border-color: #16a34a;
}

/* ---------- 信息 ---------- */
.name {
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.4;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  font-size: 13px;
  color: #8a9099;
}

.category {
  color: #6b7280;
}

.price-block {
  margin-top: 16px;
  padding: 16px;
  background: #fff7f7;
  border-radius: 8px;
}

.price {
  color: #e64545;
  font-size: 14px;
}

.price b {
  font-size: 30px;
  font-weight: 700;
}

.unit {
  margin-left: 6px;
  color: #8a9099;
  font-size: 14px;
}

.sku-block,
.qty-block {
  margin-top: 20px;
}

.block-label {
  margin-bottom: 10px;
  font-size: 14px;
  color: #6b7280;
}

.stock-tip {
  margin-left: 12px;
  font-size: 13px;
  color: #8a9099;
}

.action-row {
  margin-top: 28px;
}

.farmer-block {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  font-size: 13px;
  color: #6b7280;
}

/* ---------- 详情 ---------- */
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.desc-text {
  font-size: 14px;
  line-height: 1.8;
  color: #4b5563;
  white-space: pre-wrap;
}
</style>
