<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getGroup } from '@/api/financing'
import { groupStatusLabel, groupStatusTagType, industryLabel, formatPrice } from '@/utils/format'

/**
 * 联保小组详情 —— 小组信息 + 成员名单（农户判断是否加入、银行核验用）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const group = ref(null)

const joinStatusText = {
  0: '申请中',
  1: '已加入',
  2: '已拒绝'
}

async function load() {
  loading.value = true
  try {
    group.value = await getGroup(route.params.id)
    document.title = `${group.value?.groupNo || '小组详情'} · 融销通`
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="gd-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">联保小组详情</h2>
        </div>
        <el-button @click="router.back()">返回</el-button>
      </div>

      <div v-loading="loading">
        <el-card v-if="group" shadow="never" class="gd-head-card">
          <div class="gd-head">
            <div>
              <div class="gd-no">{{ group.groupNo }}</div>
              <div class="gd-title">{{ industryLabel(group.industry) }}联保小组 · {{ group.province }} {{ group.city }}</div>
            </div>
            <el-tag :type="groupStatusTagType(group.status)" size="large">
              {{ group.statusName || groupStatusLabel(group.status) }}
            </el-tag>
          </div>

          <el-descriptions :column="3" border class="gd-desc">
            <el-descriptions-item label="申请总额">¥{{ formatPrice(group.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="成员数">{{ group.currentMembers }}/{{ group.maxMembers }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ group.createdAt }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card v-if="group" shadow="never" class="gd-member-card">
          <template #header><span class="card-title">成员名单（{{ group.members?.length || 0 }}）</span></template>
          <el-table :data="group.members || []" size="small">
            <el-table-column prop="username" label="成员" min-width="120" />
            <el-table-column label="申请金额" width="140">
              <template #default="{ row }">¥{{ formatPrice(row.applyAmount) }}</template>
            </el-table-column>
            <el-table-column label="行业" width="120">
              <template #default="{ row }">{{ industryLabel(row.industry) }}</template>
            </el-table-column>
            <el-table-column prop="province" label="地区" min-width="120">
              <template #default="{ row }">{{ row.province }} {{ row.city }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ joinStatusText[row.joinStatus] ?? '未知' }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-empty v-if="!loading && !group" description="小组不存在" style="padding: 60px 0" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.gd-page {
  padding-bottom: 40px;
}

.gd-head-card {
  max-width: 900px;
  margin-bottom: 16px;
}

.gd-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.gd-no {
  font-size: 13px;
  color: #8a9099;
}

.gd-title {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.gd-desc {
  margin-top: 16px;
}

.gd-member-card {
  max-width: 900px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}
</style>
