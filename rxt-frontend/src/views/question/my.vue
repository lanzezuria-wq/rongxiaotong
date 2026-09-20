<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listQuestions, answerQuestion } from '@/api/question'
import { isExpert } from '@/utils/auth'
import { questionStatusLabel, questionStatusTagType } from '@/utils/format'

/**
 * 我的提问 —— 按角色分视角：普通用户看「我提的问题」，专家看「待我回答的 + 我提的」。
 * 专家可对待回答的问题直接作答。
 */
const loading = ref(false)
const records = ref([])
const query = reactive({ status: '' })

const answerDialog = ref(false)
const answering = ref(false)
const answerForm = reactive({ questionId: null, answer: '' })

const FILTER = [
  { value: '', label: '全部' },
  { value: 0, label: '待回答' },
  { value: 1, label: '已回答' }
]

async function load() {
  loading.value = true
  try {
    records.value =
      (await listQuestions({ status: query.status === '' ? undefined : query.status })) || []
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  load()
}

function openAnswer(row) {
  answerForm.questionId = row.id
  answerForm.answer = ''
  answerDialog.value = true
}

async function onSubmitAnswer() {
  if (!answerForm.answer.trim()) {
    ElMessage.warning('请输入回答内容')
    return
  }
  answering.value = true
  try {
    await answerQuestion(answerForm.questionId, answerForm.answer.trim())
    ElMessage.success('回答已提交')
    answerDialog.value = false
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    answering.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="qmy-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">我的提问</h2>
          <p class="page-desc">{{ isExpert() ? '待我回答的问题 + 我提的问题' : '我向专家提的问题' }}</p>
        </div>
      </div>

      <el-radio-group v-model="query.status" class="status-tabs" @change="onStatusChange">
        <el-radio-button v-for="s in FILTER" :key="s.label" :value="s.value">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <div v-loading="loading" class="q-list">
        <el-card v-for="q in records" :key="q.id" shadow="never" class="q-card">
          <div class="q-head">
            <span class="q-title">{{ q.title }}</span>
            <el-tag :type="questionStatusTagType(q.status)" size="small">{{ questionStatusLabel(q.status) }}</el-tag>
          </div>
          <div class="q-content">{{ q.content }}</div>
          <div class="q-meta">
            <span v-if="q.expertName">指定专家：{{ q.expertName }}</span>
            <span>{{ q.createdAt }}</span>
          </div>

          <div v-if="q.status === 1" class="q-answer">
            <div class="qa-label">回答<template v-if="q.answerByName"> · {{ q.answerByName }}</template></div>
            <div class="qa-text">{{ q.answer }}</div>
            <div class="qa-time">{{ q.answeredAt }}</div>
          </div>

          <div v-if="isExpert() && q.status === 0" class="q-actions">
            <el-button size="small" type="primary" @click="openAnswer(q)">回答</el-button>
          </div>
        </el-card>

        <el-empty v-if="!loading && records.length === 0" description="暂无提问" style="padding: 60px 0">
          <el-button type="primary" @click="$router.push('/question/ask')">去提问</el-button>
        </el-empty>
      </div>
    </div>

    <el-dialog v-model="answerDialog" title="回答提问" width="520px">
      <el-input v-model="answerForm.answer" type="textarea" :rows="6" maxlength="1000" show-word-limit
        placeholder="请输入专业回答" />
      <template #footer>
        <el-button @click="answerDialog = false">取消</el-button>
        <el-button type="primary" :loading="answering" @click="onSubmitAnswer">提交回答</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.qmy-page {
  padding-bottom: 40px;
}

.status-tabs {
  margin-bottom: 16px;
}

.q-list {
  max-width: 860px;
}

.q-card {
  margin-bottom: 12px;
}

.q-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.q-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.q-content {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
  white-space: pre-wrap;
}

.q-meta {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
  color: #8a9099;
}

.q-answer {
  margin-top: 12px;
  padding: 12px;
  background: #f6fbf8;
  border-radius: 6px;
}

.qa-label {
  font-size: 13px;
  color: #16a34a;
  font-weight: 600;
}

.qa-text {
  margin-top: 6px;
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
  white-space: pre-wrap;
}

.qa-time {
  margin-top: 6px;
  font-size: 12px;
  color: #8a9099;
}

.q-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}
</style>
