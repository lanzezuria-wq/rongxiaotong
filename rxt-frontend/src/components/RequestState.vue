<script setup>
defineProps({ loading: Boolean, error: Boolean, empty: Boolean, emptyText: { type: String, default: '这里还没有内容' } })
defineEmits(['retry'])
</script>
<template>
  <div v-if="loading" class="request-state" role="status"><el-skeleton :rows="3" animated /></div>
  <div v-else-if="error" class="request-state" role="alert"><el-icon :size="24"><Warning /></el-icon><p>内容暂时未能加载</p><span>请检查网络连接后重试。</span><el-button @click="$emit('retry')">重新加载</el-button></div>
  <div v-else-if="empty" class="request-state"><el-icon :size="28"><FolderOpened /></el-icon><p>{{ emptyText }}</p></div>
  <slot v-else />
</template>
