<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listArticles } from '@/api/article'

/**
 * 知识文章列表 —— 公开，只看已发布（status=1）。
 *
 * <p>列表接口返回的是 P1 的 ArticleVO（含完整 content，无摘要字段），
 * 这里客户端截取前 80 字做摘要展示。
 */
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

function summarize(content) {
  if (!content) return ''
  const flat = String(content).replace(/\s+/g, ' ').trim()
  return flat.length > 80 ? flat.slice(0, 80) + '…' : flat
}

async function load() {
  loading.value = true
  try {
    const data = await listArticles({
      status: 1,
      keyword: query.keyword || undefined,
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

function onSearch() {
  query.page = 1
  load()
}

function onPageChange() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="article-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">知识文章</h2>
          <p class="page-desc">农业种植、养殖与经营管理知识</p>
        </div>
      </div>

      <div class="search-row">
        <el-input v-model="query.keyword" placeholder="搜索文章标题" clearable style="max-width: 320px"
          @keyup.enter="onSearch" @clear="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>

      <div v-loading="loading" class="article-list">
        <el-card v-for="a in records" :key="a.id" shadow="hover" class="article-card" @click="router.push(`/articles/${a.id}`)">
          <div class="ac-head">
            <span class="ac-title">{{ a.title }}</span>
          </div>
          <div class="ac-summary">{{ summarize(a.content) }}</div>
          <div class="ac-meta">
            <span>{{ a.authorName || '匿名' }}</span>
            <span class="ac-dot">·</span>
            <span><el-icon><View /></el-icon> {{ a.viewCount ?? 0 }}</span>
            <span class="ac-dot">·</span>
            <span>{{ a.createTime }}</span>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0" description="暂无文章" style="padding: 60px 0" />

        <div v-if="total > query.size" class="pagination-bar">
          <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
            layout="prev, pager, next, total" background @current-change="onPageChange" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-page {
  padding-bottom: 40px;
}

.search-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.article-list {
  max-width: 900px;
}

.article-card {
  margin-bottom: 12px;
  cursor: pointer;
}

.ac-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.ac-summary {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.ac-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 12px;
  color: #8a9099;
}

.ac-meta .el-icon {
  vertical-align: -2px;
}

.ac-dot {
  color: #d1d5db;
}
</style>
