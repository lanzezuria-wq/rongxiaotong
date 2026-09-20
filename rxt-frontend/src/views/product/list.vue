<script setup>
import { reactive, ref, onMounted } from 'vue'
import { listProducts } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'
import { PRODUCT_CATEGORY_OPTIONS } from '@/utils/format'

/**
 * 商品列表页 —— 搜索 + 分类筛选 + 排序 + 分页（游客可访问）。
 *
 * <p>排序取值与后端 ProductServiceImpl.applySort 对齐：
 * default 综合（销量优先）/ price_asc / price_desc / sales / newest。
 * 后端对未知 sort 值会静默退回综合排序，这里给固定的下拉项避免传错。
 */
const loading = ref(false)
const records = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  categoryCode: '',
  sort: 'default',
  page: 1,
  size: 12
})

const SORT_OPTIONS = [
  { value: 'default', label: '综合' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'sales', label: '销量优先' },
  { value: 'newest', label: '最新上架' }
]

async function load() {
  loading.value = true
  try {
    const data = await listProducts({
      keyword: query.keyword || undefined,
      categoryCode: query.categoryCode || undefined,
      sort: query.sort,
      page: query.page,
      size: query.size
    })
    records.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

/** 搜索 / 换分类 / 换排序都会把页码重置回第 1 页 */
function onSearch() {
  query.page = 1
  load()
}

function onCategoryChange() {
  query.page = 1
  load()
}

function onSortChange() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="product-list-page">
    <div class="container">
      <el-card shadow="never" class="filter-card">
        <div class="filter-row">
          <el-input
            v-model="query.keyword"
            placeholder="搜索农产品名称"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </div>

        <div class="filter-row">
          <span class="filter-label">分类</span>
          <el-radio-group v-model="query.categoryCode" @change="onCategoryChange">
            <el-radio-button :value="''">全部</el-radio-button>
            <el-radio-button v-for="c in PRODUCT_CATEGORY_OPTIONS" :key="c.value" :value="c.value">
              {{ c.label }}
            </el-radio-button>
          </el-radio-group>

          <span class="sort-label">排序</span>
          <el-select v-model="query.sort" class="sort-select" @change="onSortChange">
            <el-option v-for="s in SORT_OPTIONS" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
        </div>
      </el-card>

      <div v-loading="loading" class="list-body">
        <div v-if="records.length" class="product-grid">
          <ProductCard v-for="p in records" :key="p.id" :product="p" />
        </div>
        <el-empty v-else-if="!loading" description="没有找到相关商品" />

        <div v-if="total > query.size" class="pagination-bar">
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.size"
            :total="total"
            layout="prev, pager, next, total"
            background
            @current-change="load"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-list-page {
  padding: 20px 0 32px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-row + .filter-row {
  margin-top: 14px;
}

.search-input {
  max-width: 320px;
}

.filter-label,
.sort-label {
  font-size: 14px;
  color: #6b7280;
  white-space: nowrap;
}

.sort-select {
  width: 160px;
}

.list-body {
  min-height: 300px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
