<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listBankGroups } from '@/api/bank'
import GroupCard from '@/components/GroupCard.vue'

/**
 * 银行端联保小组列表 —— 审批工作台入口。
 * 后端默认返回「已成型」的小组（待处理的排前面），按状态筛选后可精确查看。
 */
const router = useRouter()
const loading = ref(false)
const groups = ref([])
const query = reactive({ status: '' })

const FILTER = [
  { value: '', label: '全部' },
  { value: 1, label: '待审批' },
  { value: 3, label: '已通过' },
  { value: 4, label: '已拒绝' }
]

async function load() {
  loading.value = true
  try {
    groups.value = (await listBankGroups({ status: query.status === '' ? undefined : query.status })) || []
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  load()
}

/** 待审批（1 已匹配 / 2 待银行审批）的才能审批 */
function isPending(g) {
  return g.status === 1 || g.status === 2
}

onMounted(load)
</script>

<template>
  <div class="bank-groups-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">联保小组审批</h2>
          <p class="page-desc">审批已成型的联保小组融资申请</p>
        </div>
        <el-button @click="$router.push('/bank/stats')">统计看板</el-button>
      </div>

      <el-radio-group v-model="query.status" class="status-tabs" @change="onStatusChange">
        <el-radio-button v-for="s in FILTER" :key="s.label" :value="s.value">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <div v-loading="loading" class="group-grid">
        <GroupCard v-for="g in groups" :key="g.id" :group="g">
          <el-button size="small" @click="$router.push(`/bank/approve/${g.id}`)">
            {{ isPending(g) ? '去审批' : '查看' }}
          </el-button>
        </GroupCard>
      </div>

      <el-empty v-if="!loading && groups.length === 0" description="暂无联保小组" style="padding: 60px 0" />
    </div>
  </div>
</template>

<style scoped>
.bank-groups-page {
  padding-bottom: 40px;
}

.status-tabs {
  margin-bottom: 16px;
}

.group-grid {
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 200px;
}
</style>
