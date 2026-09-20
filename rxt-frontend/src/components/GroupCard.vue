<script setup>
import { computed } from 'vue'
import { groupStatusLabel, groupStatusTagType, industryLabel, formatPrice } from '@/utils/format'

/**
 * 联保小组卡片 —— 融资「可加入小组」、银行「审批列表」共用的展示单元。
 *
 * <p>只做展示，不含请求与跳转：操作按钮由父组件通过默认插槽注入，
 * 跳转逻辑也由父组件决定（农户端去小组详情、银行端去审批页）。
 */
const props = defineProps({
  /** 小组摘要（GuaranteeGroupVO）：groupNo/industry/province/city/totalAmount/currentMembers/maxMembers/status/statusName/remainingToForm */
  group: { type: Object, required: true }
})

const percent = computed(() => {
  const cur = props.group.currentMembers || 0
  const max = props.group.maxMembers || 10
  return Math.min(100, Math.round((cur / max) * 100))
})
</script>

<template>
  <el-card shadow="hover" class="group-card" :body-style="{ padding: '16px' }">
    <div class="gc-head">
      <span class="gc-no">{{ group.groupNo }}</span>
      <el-tag :type="groupStatusTagType(group.status)" size="small">
        {{ group.statusName || groupStatusLabel(group.status) }}
      </el-tag>
    </div>

    <div class="gc-title">
      {{ industryLabel(group.industry) }}联保小组
      <span class="gc-region">{{ group.province }} · {{ group.city }}</span>
    </div>

    <div class="gc-amount">申请总额 <b>¥{{ formatPrice(group.totalAmount) }}</b></div>

    <div class="gc-progress">
      <el-progress :percentage="percent" :stroke-width="10" :show-text="false" />
      <div class="gc-members">
        <span>成员 {{ group.currentMembers }}/{{ group.maxMembers }}</span>
        <span v-if="group.remainingToForm > 0" class="gc-remain">还差 {{ group.remainingToForm }} 人成团</span>
        <span v-else class="gc-formed">已成型，进入银行审批</span>
      </div>
    </div>

    <div v-if="$slots.default" class="gc-actions">
      <slot />
    </div>
  </el-card>
</template>

<style scoped>
.group-card {
  border-radius: 8px;
}

.gc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.gc-no {
  font-size: 12px;
  color: #8a9099;
}

.gc-title {
  margin-top: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.gc-region {
  margin-left: 6px;
  font-size: 13px;
  font-weight: 400;
  color: #8a9099;
}

.gc-amount {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
}

.gc-amount b {
  color: #e64545;
  font-size: 16px;
}

.gc-progress {
  margin-top: 12px;
}

.gc-members {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 12px;
  color: #8a9099;
}

.gc-remain {
  color: #d97706;
}

.gc-formed {
  color: #16a34a;
}

.gc-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
