<script setup>
import { ref, computed, onMounted } from 'vue'
import { getBankStats } from '@/api/bank'
import { formatPrice } from '@/utils/format'
import RequestState from '@/components/RequestState.vue'
const loading = ref(true)
const error = ref(false)
const stats = ref(null)
const distribution = computed(() => [ ['待审批', stats.value?.pendingGroups || 0], ['已通过', stats.value?.approvedGroups || 0], ['已拒绝', stats.value?.rejectedGroups || 0] ])
async function load() {
  loading.value = true; error.value = false
  try { stats.value = await getBankStats() } catch { error.value = true } finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <div class="stats-page"><div class="container"><div class="page-header"><div><h1 class="page-title">融资统计</h1><p class="page-desc">查看联保小组处理进度与累计核定额度。</p></div><el-button type="primary" @click="$router.push('/bank/groups')">处理待审批小组<el-icon><ArrowRight /></el-icon></el-button></div>
    <RequestState :loading="loading" :error="error" @retry="load"><div v-if="stats"><div class="bank-stats-summary"><div><span>联保小组总数</span><strong>{{ stats.totalGroups }}</strong></div><div class="pending"><span>待审批小组</span><strong>{{ stats.pendingGroups }}</strong></div><div><span>已通过小组</span><strong>{{ stats.approvedGroups }}</strong></div><div><span>已拒绝小组</span><strong>{{ stats.rejectedGroups }}</strong></div></div>
      <div class="bank-stats-detail"><section><h2>审批状态分布</h2><div v-for="[label, value] in distribution" :key="label" class="distribution-row"><span>{{ label }}</span><div class="distribution-track"><span :style="{ width: `${stats.totalGroups ? value / stats.totalGroups * 100 : 0}%` }" /></div><b>{{ value }}</b></div><p>统计范围为当前数据库中的联保小组，招募中的小组不计入待审批。</p></section><section><h2>已通过核定额度合计</h2><div class="approved-total">¥{{ formatPrice(stats.totalApprovedAmount) }}</div><p>核定额度用于展示审批结果，不代表实际放款。</p><el-button @click="load"><el-icon><Refresh /></el-icon>更新统计</el-button></section></div>
    </div></RequestState><p class="hint" style="margin-top: 24px">本项目的金融服务为课程演示流程，未接入真实征信与放款。</p></div></div>
</template>
