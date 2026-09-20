<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listExperts } from '@/api/expert'

/**
 * 专家列表 —— 公开，农户挑选专家入口。
 */
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ specialty: '', page: 1, size: 12 })

function avatarText(name) {
  return name ? name.slice(0, 1) : '专'
}

async function load() {
  loading.value = true
  try {
    const data = await listExperts({
      specialty: query.specialty || undefined,
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
  <div class="expert-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">农业专家</h2>
          <p class="page-desc">向农业专家在线提问、预约咨询</p>
        </div>
      </div>

      <div class="search-row">
        <el-input v-model="query.specialty" placeholder="按专业领域搜索（如：蔬菜 / 育种）" clearable
          style="max-width: 320px" @keyup.enter="onSearch" @clear="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>

      <div v-loading="loading" class="expert-grid">
        <el-card v-for="e in records" :key="e.id" shadow="hover" class="expert-card" @click="router.push(`/experts/${e.id}`)">
          <div class="ec-head">
            <el-avatar :size="52" class="ec-avatar">{{ avatarText(e.realName) }}</el-avatar>
            <div class="ec-info">
              <div class="ec-name">
                {{ e.realName }}
                <span class="ec-title">{{ e.title }}</span>
              </div>
              <div class="ec-specialty">{{ e.specialty }}</div>
              <div class="ec-org">{{ e.org }}</div>
            </div>
          </div>
          <div class="ec-foot">
            <span class="ec-rating"><el-icon><Star /></el-icon> {{ e.rating ?? '-' }} 分</span>
            <span class="ec-answer">已回答 {{ e.answerCount ?? 0 }} 个问题</span>
          </div>
        </el-card>
      </div>

      <el-empty v-if="!loading && records.length === 0" description="暂无专家" style="padding: 60px 0" />

      <div v-if="total > query.size" class="pagination-bar">
        <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
          layout="prev, pager, next, total" background @current-change="onPageChange" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.expert-page {
  padding-bottom: 40px;
}

.search-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.expert-grid {
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.expert-card {
  cursor: pointer;
}

.ec-head {
  display: flex;
  gap: 12px;
  align-items: center;
}

.ec-avatar {
  background: #16a34a;
  font-size: 20px;
  flex-shrink: 0;
}

.ec-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.ec-title {
  margin-left: 6px;
  font-size: 12px;
  color: #8a9099;
  font-weight: 400;
}

.ec-specialty {
  margin-top: 4px;
  font-size: 13px;
  color: #16a34a;
}

.ec-org {
  margin-top: 2px;
  font-size: 12px;
  color: #8a9099;
}

.ec-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  font-size: 12px;
  color: #8a9099;
}

.ec-rating {
  color: #d97706;
}

.ec-rating .el-icon {
  vertical-align: -2px;
}
</style>
