<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBankStats } from '@/api/bank'
import { formatPrice } from '@/utils/format'

/**
 * 银行端融资统计 —— 审批员工作看板（实时统计，不缓存）。
 */
const router = useRouter()
const loading = ref(false)
const stats = ref(null)

async function load() {
  loading.value = true
  try {
    stats.value = await getBankStats()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="stats-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">融资统计</h2>
          <p class="page-desc">联保小组审批工作看板</p>
        </div>
        <el-button @click="router.push('/bank/groups')">返回审批列表</el-button>
      </div>

      <div v-loading="loading" class="stat-grid">
        <div class="stat-card">
          <div class="stat-num">{{ stats?.totalGroups ?? 0 }}</div>
          <div class="stat-label">小组总数</div>
        </div>
        <div class="stat-card accent">
          <div class="stat-num">{{ stats?.pendingGroups ?? 0 }}</div>
          <div class="stat-label">待审批</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ stats?.approvedGroups ?? 0 }}</div>
          <div class="stat-label">已通过</div>
        </div>
        <div class="stat-card">
          <div class="stat-num">{{ stats?.rejectedGroups ?? 0 }}</div>
          <div class="stat-label">已拒绝</div>
        </div>
        <div class="stat-card wide">
          <div class="stat-num">¥{{ formatPrice(stats?.totalApprovedAmount ?? 0) }}</div>
          <div class="stat-label">已通过核定额度合计</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-page {
  padding-bottom: 40px;
}

.stat-grid {
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  padding: 24px;
  text-align: center;
  background: #fff;
  border-radius: 8px;
}

.stat-card.accent .stat-num {
  color: #d97706;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #16a34a;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #8a9099;
}

.stat-card.wide {
  grid-column: span 2;
}
</style>
