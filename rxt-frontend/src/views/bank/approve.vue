<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getGroup } from '@/api/financing'
import { approveGroup } from '@/api/bank'
import { groupStatusLabel, groupStatusTagType, industryLabel, formatPrice } from '@/utils/format'

/**
 * 银行审批页 —— 展示小组与成员，提交审批结论。
 *
 * <p>信用分/利率/核定额度三项可不填：Mock 模式开启时后端会自动生成
 * （信用分 60~100、利率 3.85~7.00%、核定额度按申请总额 80%~100%）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const group = ref(null)

const form = reactive({
  result: 'APPROVED',
  creditScore: null,
  interestRate: null,
  decisionAmount: null,
  remark: ''
})

const joinStatusText = { 0: '申请中', 1: '已加入', 2: '已拒绝' }

async function load() {
  loading.value = true
  try {
    group.value = await getGroup(route.params.id)
    document.title = `${group.value?.groupNo || '审批'} · 融销通`
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!form.result) {
    ElMessage.warning('请选择审批结果')
    return
  }
  if (form.result === 'REJECTED' && !form.remark.trim()) {
    ElMessage.warning('拒绝时请填写审批意见')
    return
  }
  submitting.value = true
  try {
    await approveGroup({
      groupId: group.value.id,
      result: form.result,
      creditScore: form.creditScore ?? undefined,
      interestRate: form.interestRate ?? undefined,
      decisionAmount: form.decisionAmount ?? undefined,
      remark: form.remark.trim() || undefined
    })
    ElMessage.success(form.result === 'APPROVED' ? '已通过审批' : '已拒绝该小组')
    router.push('/bank/groups')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="approve-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">审批联保小组</h2>
        </div>
        <el-button @click="router.back()">返回</el-button>
      </div>

      <div v-loading="loading">
        <template v-if="group">
          <el-card shadow="never" class="block">
            <div class="g-head">
              <div>
                <div class="g-no">{{ group.groupNo }}</div>
                <div class="g-title">{{ industryLabel(group.industry) }}联保小组 · {{ group.province }} {{ group.city }}</div>
              </div>
              <el-tag :type="groupStatusTagType(group.status)">{{ group.statusName || groupStatusLabel(group.status) }}</el-tag>
            </div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="申请总额">¥{{ formatPrice(group.totalAmount) }}</el-descriptions-item>
              <el-descriptions-item label="成员数">{{ group.currentMembers }}/{{ group.maxMembers }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="never" class="block">
            <template #header><span class="card-title">成员核验（{{ group.members?.length || 0 }}）</span></template>
            <el-table :data="group.members || []" size="small">
              <el-table-column prop="username" label="成员" min-width="100" />
              <el-table-column label="申请金额" width="140">
                <template #default="{ row }">¥{{ formatPrice(row.applyAmount) }}</template>
              </el-table-column>
              <el-table-column label="行业" width="120">
                <template #default="{ row }">{{ industryLabel(row.industry) }}</template>
              </el-table-column>
              <el-table-column label="地区" min-width="120">
                <template #default="{ row }">{{ row.province }} {{ row.city }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">{{ joinStatusText[row.joinStatus] ?? '未知' }}</template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="block">
            <template #header><span class="card-title">审批结论</span></template>
            <el-form :model="form" label-width="90px">
              <el-form-item label="审批结果">
                <el-radio-group v-model="form.result">
                  <el-radio-button value="APPROVED">通过</el-radio-button>
                  <el-radio-button value="REJECTED">拒绝</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="信用评分">
                <el-input-number v-model="form.creditScore" :min="0" :max="100" />
                <span class="hint">留空由 Mock 自动生成</span>
              </el-form-item>
              <el-form-item label="年化利率">
                <el-input-number v-model="form.interestRate" :min="3.85" :max="7.00" :precision="2" :step="0.01" />
                <span class="hint">%（留空自动生成）</span>
              </el-form-item>
              <el-form-item label="核定额度">
                <el-input-number v-model="form.decisionAmount" :min="0" :step="10000" />
                <span class="hint">元（留空按申请额 80%~100% 生成）</span>
              </el-form-item>
              <el-form-item label="审批意见">
                <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit
                  placeholder="拒绝时必填" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">提交审批</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </template>

        <el-empty v-if="!loading && !group" description="小组不存在" style="padding: 60px 0" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.approve-page {
  padding-bottom: 40px;
}

.block {
  max-width: 900px;
  margin-bottom: 16px;
}

.g-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.g-no {
  font-size: 13px;
  color: #8a9099;
}

.g-title {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #8a9099;
}
</style>
