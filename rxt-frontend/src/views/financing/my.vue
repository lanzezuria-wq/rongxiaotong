<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFinancing, getFinancing, cancelFinancing } from '@/api/financing'
import {
  financingStatusLabel,
  financingStatusTagType,
  industryLabel,
  fundUsageLabel,
  loanTermLabel,
  formatPrice
} from '@/utils/format'

/**
 * 我的融资 —— 申请列表（状态筛选 + 详情 + 取消）。
 */
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

const STATUS_FILTER = [
  { value: '', label: '全部' },
  { value: 0, label: '待匹配' },
  { value: 1, label: '已匹配' },
  { value: 2, label: '待银行审批' },
  { value: 3, label: '已通过' },
  { value: 4, label: '已拒绝' },
  { value: 5, label: '已取消' }
]

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await listFinancing({
      status: query.status === '' ? undefined : query.status,
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

function onStatusChange() {
  query.page = 1
  load()
}

function onPageChange() {
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getFinancing(row.id)
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    detailLoading.value = false
  }
}

async function onCancel(row) {
  try {
    await ElMessageBox.confirm('确定取消该融资申请吗？取消后会自动退出所在联保小组。', '提示', {
      type: 'warning',
      confirmButtonText: '取消申请',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  try {
    await cancelFinancing(row.id)
    ElMessage.success('申请已取消')
    load()
  } catch (e) {
    // 拦截器已统一提示
  }
}

onMounted(load)
</script>

<template>
  <div class="my-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">我的融资</h2>
          <p class="page-desc">我的融资申请与审批进度</p>
        </div>
        <el-button type="primary" @click="$router.push('/financing/apply')">申请融资</el-button>
      </div>

      <el-radio-group v-model="query.status" class="status-tabs" @change="onStatusChange">
        <el-radio-button v-for="s in STATUS_FILTER" :key="s.label" :value="s.value">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <div v-loading="loading" class="fin-list">
        <el-card v-for="r in records" :key="r.id" shadow="never" class="fin-card">
          <div class="fin-head">
            <div class="fin-amount">¥{{ formatPrice(r.amount) }}</div>
            <el-tag :type="financingStatusTagType(r.status)">{{ r.statusName || financingStatusLabel(r.status) }}</el-tag>
          </div>

          <div class="fin-meta">
            <span>{{ industryLabel(r.industry) }}</span>
            <span>{{ fundUsageLabel(r.usage) }}</span>
            <span>期限 {{ loanTermLabel(r.loanTerm) }}</span>
            <span>{{ r.province }} {{ r.city }}</span>
          </div>
          <div class="fin-time">申请时间 {{ r.createdAt }}</div>

          <div class="fin-actions">
            <el-button size="small" @click="openDetail(r)">详情</el-button>
            <el-button v-if="r.status === 0" size="small" type="primary" @click="$router.push('/financing/groups')">
              找小组
            </el-button>
            <el-button v-if="[0, 1, 2].includes(r.status)" size="small" type="danger" plain @click="onCancel(r)">
              取消
            </el-button>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0" description="暂无融资申请" style="padding: 60px 0" />

        <div v-if="total > query.size" class="pagination-bar">
          <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
            layout="prev, pager, next, total" background @current-change="onPageChange" />
        </div>
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="融资申请详情" width="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申请金额">¥{{ formatPrice(detail.amount) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="financingStatusTagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="行业">{{ industryLabel(detail.industry) }}</el-descriptions-item>
            <el-descriptions-item label="资金用途">{{ fundUsageLabel(detail.usage) }}</el-descriptions-item>
            <el-descriptions-item label="贷款期限">{{ loanTermLabel(detail.loanTerm) }}</el-descriptions-item>
            <el-descriptions-item label="地区">{{ detail.province }} {{ detail.city }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-section">
            <div class="detail-label">用途说明</div>
            <div class="detail-text">{{ detail.description || '无' }}</div>
          </div>

          <div v-if="detail.group" class="detail-section">
            <div class="detail-label">所属联保小组</div>
            <div class="detail-text">
              {{ detail.group.groupNo }}（{{ detail.group.currentMembers }}/{{ detail.group.maxMembers }} 人，
              {{ detail.group.statusName }}）
            </div>
          </div>

          <div v-if="detail.approval" class="detail-section">
            <div class="detail-label">银行审批结果</div>
            <div class="detail-text">
              {{ detail.approval.result === 'APPROVED' ? '已通过' : '已拒绝' }}
              <template v-if="detail.approval.result === 'APPROVED'">
                · 信用分 {{ detail.approval.creditScore }} · 利率 {{ detail.approval.interestRate }}%
                · 核定额度 ¥{{ formatPrice(detail.approval.decisionAmount) }}
              </template>
              <div v-if="detail.approval.remark" class="detail-remark">意见：{{ detail.approval.remark }}</div>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.my-page {
  padding-bottom: 40px;
}

.status-tabs {
  margin-bottom: 16px;
}

.fin-list {
  max-width: 900px;
}

.fin-card {
  margin-bottom: 12px;
}

.fin-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fin-amount {
  font-size: 20px;
  font-weight: 700;
  color: #e64545;
}

.fin-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
}

.fin-time {
  margin-top: 6px;
  font-size: 12px;
  color: #8a9099;
}

.fin-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.detail-section {
  margin-top: 14px;
}

.detail-label {
  font-size: 13px;
  color: #8a9099;
  margin-bottom: 4px;
}

.detail-text {
  font-size: 14px;
  color: #1f2329;
  line-height: 1.6;
}

.detail-remark {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}
</style>
