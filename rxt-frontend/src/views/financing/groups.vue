<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listGroups, joinGroup, listFinancing } from '@/api/financing'
import { INDUSTRY_OPTIONS } from '@/utils/format'
import GroupCard from '@/components/GroupCard.vue'

/**
 * 联保小组列表 —— 浏览可加入的小组，并支持直接加入。
 *
 * <p>「加入」要传 applyId（对应某条融资申请），所以加载时顺手取一条
 * 我当前的「待匹配」申请；没有的话点加入会引导先去申请融资。
 */
const router = useRouter()
const loading = ref(false)
const groups = ref([])
const activeApplyId = ref(null)
const joiningId = ref(null)

const query = reactive({ industry: '', province: '' })

async function load() {
  loading.value = true
  try {
    groups.value =
      (await listGroups({ industry: query.industry || undefined, province: query.province || undefined })) || []
    const data = await listFinancing({ page: 1, size: 1, status: 0 })
    activeApplyId.value = data?.records?.[0]?.id ?? null
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

async function onJoin(group) {
  if (!activeApplyId.value) {
    ElMessage.warning('请先提交融资申请')
    router.push('/financing/apply')
    return
  }
  joiningId.value = group.id
  try {
    await joinGroup({ groupId: group.id, applyId: activeApplyId.value })
    ElMessage.success('已加入联保小组')
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    joiningId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="groups-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">联保小组</h2>
          <p class="page-desc">加入 3~10 人联保小组，互相担保向银行申请融资</p>
        </div>
        <el-button type="primary" @click="$router.push('/financing/apply')">申请融资</el-button>
      </div>

      <el-card shadow="never" class="filter-card">
        <div class="filter-row">
          <el-select v-model="query.industry" clearable placeholder="所属行业" style="width: 180px">
            <el-option v-for="o in INDUSTRY_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
          <el-input v-model.trim="query.province" clearable placeholder="省份" style="width: 160px" />
          <el-button type="primary" @click="onSearch">筛选</el-button>
        </div>
      </el-card>

      <div v-loading="loading" class="group-grid">
        <GroupCard v-for="g in groups" :key="g.id" :group="g">
          <el-button size="small" @click="$router.push(`/financing/group/${g.id}`)">详情</el-button>
          <el-button size="small" type="primary" :loading="joiningId === g.id" @click="onJoin(g)">加入</el-button>
        </GroupCard>
      </div>

      <el-empty v-if="!loading && groups.length === 0" description="暂无可加入的小组，可先发起申请或自己创建小组"
        style="padding: 60px 0" />
    </div>
  </div>
</template>

<style scoped>
.groups-page {
  padding-bottom: 40px;
}

.filter-card {
  max-width: 900px;
  margin-bottom: 16px;
}

.filter-row {
  display: flex;
  gap: 12px;
}

.group-grid {
  max-width: 900px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 200px;
}
</style>
