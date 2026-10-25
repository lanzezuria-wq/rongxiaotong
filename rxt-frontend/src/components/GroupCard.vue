<script setup>
import { computed } from 'vue'
import { groupStatusLabel, groupStatusTagType, industryLabel, formatPrice } from '@/utils/format'
const props = defineProps({ group: { type: Object, required: true } })
const seats = computed(() => Math.min(props.group.maxMembers || 10, Math.max(3, props.group.currentMembers || 0)))
</script>
<template>
  <el-card shadow="never" class="group-card" :body-style="{ padding: '24px' }">
    <div class="gc-head"><span class="gc-no">{{ group.groupNo }}</span><el-tag :type="groupStatusTagType(group.status)" size="small">{{ group.statusName || groupStatusLabel(group.status) }}</el-tag></div>
    <h2 class="gc-title">{{ industryLabel(group.industry) }}联保小组<span class="gc-region">{{ group.province }} · {{ group.city }}</span></h2>
    <div class="gc-amount">申请总额<b>¥{{ formatPrice(group.totalAmount) }}</b></div>
    <div class="gc-seats" :aria-label="`已有 ${group.currentMembers || 0} 位成员，3 人成组`"><span v-for="seat in seats" :key="seat" class="gc-seat" :class="{ filled: seat <= (group.currentMembers || 0) }"><el-icon><User v-if="seat <= (group.currentMembers || 0)" /><Plus v-else /></el-icon></span></div>
    <div class="gc-members"><span>{{ group.currentMembers || 0 }} 位成员已加入</span><span v-if="group.remainingToForm > 0" class="gc-remain">再加入 {{ group.remainingToForm }} 人即可成组</span><span v-else class="gc-formed">已满足成组条件</span></div>
    <p class="gc-capacity">满 3 人成组 · 最多 {{ group.maxMembers || 10 }} 人</p>
    <div v-if="$slots.default" class="gc-actions"><slot /></div>
  </el-card>
</template>
