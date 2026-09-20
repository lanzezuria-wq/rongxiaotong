<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExpert } from '@/api/expert'

/**
 * 专家详情 —— 简介 + 近期文章 + 提问入口。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const expert = ref(null)

function avatarText(name) {
  return name ? name.slice(0, 1) : '专'
}

async function load() {
  loading.value = true
  try {
    expert.value = await getExpert(route.params.id)
    document.title = `${expert.value?.realName || '专家详情'} · 融销通`
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="expert-detail-page">
    <div class="container">
      <div v-loading="loading" class="detail-wrap">
        <template v-if="expert">
          <el-card shadow="never" class="profile-card">
            <div class="pf-head">
              <el-avatar :size="72" class="pf-avatar">{{ avatarText(expert.realName) }}</el-avatar>
              <div class="pf-info">
                <div class="pf-name">
                  {{ expert.realName }}
                  <span class="pf-title">{{ expert.title }}</span>
                </div>
                <div class="pf-specialty">{{ expert.specialty }}</div>
                <div class="pf-org">{{ expert.org }}</div>
              </div>
              <div class="pf-actions">
                <el-button type="primary" @click="router.push(`/question/ask?expertId=${expert.id}`)">
                  向 TA 提问
                </el-button>
              </div>
            </div>

            <el-descriptions :column="2" border class="pf-desc">
              <el-descriptions-item label="评分">{{ expert.rating ?? '-' }} 分</el-descriptions-item>
              <el-descriptions-item label="已回答">{{ expert.answerCount ?? 0 }} 个问题</el-descriptions-item>
            </el-descriptions>

            <div class="pf-intro-section">
              <div class="pf-intro-label">专家简介</div>
              <div class="pf-intro">{{ expert.intro || '暂无简介' }}</div>
            </div>
          </el-card>

          <el-card shadow="never" class="recent-card">
            <template #header><span class="card-title">近期文章</span></template>
            <div v-if="expert.recentArticles?.length" class="recent-list">
              <div v-for="a in expert.recentArticles" :key="a.id" class="recent-item"
                @click="router.push(`/articles/${a.id}`)">
                <span class="recent-title">{{ a.title }}</span>
                <span class="recent-views"><el-icon><View /></el-icon> {{ a.viewCount ?? 0 }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无文章" :image-size="60" />
          </el-card>
        </template>

        <el-empty v-if="!loading && !expert" description="专家不存在" style="padding: 60px 0">
          <el-button type="primary" @click="$router.push('/experts')">返回专家列表</el-button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<style scoped>
.expert-detail-page {
  padding-bottom: 40px;
}

.detail-wrap {
  max-width: 860px;
}

.profile-card {
  margin-bottom: 16px;
}

.pf-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.pf-avatar {
  background: #16a34a;
  font-size: 28px;
  flex-shrink: 0;
}

.pf-info {
  flex: 1;
}

.pf-name {
  font-size: 20px;
  font-weight: 600;
  color: #1f2329;
}

.pf-title {
  margin-left: 8px;
  font-size: 14px;
  font-weight: 400;
  color: #8a9099;
}

.pf-specialty {
  margin-top: 4px;
  font-size: 14px;
  color: #16a34a;
}

.pf-org {
  margin-top: 2px;
  font-size: 13px;
  color: #8a9099;
}

.pf-desc {
  margin-top: 16px;
}

.pf-intro-section {
  margin-top: 16px;
}

.pf-intro-label {
  font-size: 13px;
  color: #8a9099;
  margin-bottom: 6px;
}

.pf-intro {
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
  white-space: pre-wrap;
}

.recent-card .card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-title {
  font-size: 14px;
  color: #1f2329;
}

.recent-title:hover {
  color: #16a34a;
}

.recent-views {
  font-size: 12px;
  color: #8a9099;
}

.recent-views .el-icon {
  vertical-align: -2px;
}
</style>
