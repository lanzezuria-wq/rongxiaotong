<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listOperationLogs } from '@/api/admin'
import RequestState from '@/components/RequestState.vue'
const loading = ref(false)
const error = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', success: '' })
async function load() {
  loading.value = true; error.value = false
  try {
    const data = await listOperationLogs({ ...query, keyword: query.keyword || undefined, success: query.success === '' ? undefined : query.success })
    rows.value = data.records || []; total.value = data.total || 0
  } catch { error.value = true } finally { loading.value = false }
}
function search() { query.page = 1; load() }
function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' }
onMounted(load)
</script>
<template>
  <div class="page-container"><div class="page-header"><div><h1 class="page-title">操作日志</h1><p class="page-desc">追溯管理操作与结果，使用请求编号定位一次请求。</p></div><el-button @click="load"><el-icon><Refresh /></el-icon>刷新记录</el-button></div>
    <div class="filter-bar"><el-form inline @submit.prevent="search"><el-form-item label="操作检索"><el-input v-model="query.keyword" placeholder="操作名称、路径或请求编号" clearable @keyup.enter="search" @clear="search" style="width: 280px" /></el-form-item><el-form-item label="操作结果"><el-select v-model="query.success" style="width: 140px" @change="search"><el-option label="全部结果" value="" /><el-option label="成功" :value="1" /><el-option label="未完成" :value="0" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">查询日志</el-button></el-form-item></el-form></div>
    <RequestState :error="error" @retry="load"><div class="table-card" v-loading="loading"><el-table :data="rows" empty-text="暂无匹配的操作记录"><el-table-column prop="operation" label="管理操作" min-width="150" /><el-table-column label="操作人" min-width="140"><template #default="{ row }"><div>{{ row.phone || (row.userId ? `账户 ${row.userId}` : '未认证请求') }}</div><span class="muted">{{ row.ip || '服务调用' }}</span></template></el-table-column><el-table-column prop="params" label="操作对象" min-width="130"><template #default="{ row }">{{ row.params ? row.params.replace('targetId=', '编号 ') : '—' }}</template></el-table-column><el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'warning'" size="small">{{ row.success ? '成功' : '未完成' }}</el-tag></template></el-table-column><el-table-column label="发生时间" min-width="175"><template #default="{ row }">{{ time(row.createTime) }}</template></el-table-column><el-table-column label="请求信息" min-width="220"><template #default="{ row }"><div>{{ row.method }} {{ row.uri }}</div><span class="muted request-id">{{ row.requestId || '—' }}</span><span v-if="row.errorMsg" class="status-rejected"> · {{ row.errorMsg }}</span></template></el-table-column></el-table><div class="pagination-bar"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" /></div></div></RequestState>
  </div>
</template>
<style scoped>.request-id { font-size: 11px; overflow-wrap: anywhere; }</style>
