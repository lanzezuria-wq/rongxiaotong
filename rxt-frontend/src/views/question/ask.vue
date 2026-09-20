<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { askQuestion } from '@/api/question'
import { listExperts } from '@/api/expert'

/**
 * 在线提问 —— 可定向指定某位专家，也可面向所有专家。
 */
const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const submitting = ref(false)
const experts = ref([])

const form = reactive({ expertId: null, title: '', content: '' })

const rules = {
  title: [
    { required: true, message: '请输入问题标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入问题描述', trigger: 'blur' },
    { max: 500, message: '描述不超过 500 字', trigger: 'blur' }
  ]
}

async function loadExperts() {
  try {
    const data = await listExperts({ page: 1, size: 100 })
    experts.value = data?.records || []
  } catch (e) {
    // 拦截器已统一提示
  }
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await askQuestion({
      expertId: form.expertId ?? undefined,
      title: form.title,
      content: form.content
    })
    ElMessage.success('提问已提交')
    router.push('/question/my')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadExperts()
  // 从专家详情页「向 TA 提问」跳转过来时，预选该专家
  const expertId = Number(route.query.expertId)
  if (!Number.isNaN(expertId)) form.expertId = expertId
})
</script>

<template>
  <div class="ask-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">在线提问</h2>
          <p class="page-desc">指定专家定向提问，或不指定面向所有专家</p>
        </div>
      </div>

      <el-card shadow="never" class="ask-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
          <el-form-item label="指定专家">
            <el-select v-model="form.expertId" clearable placeholder="不指定则所有专家可见" style="width: 280px">
              <el-option v-for="e in experts" :key="e.id" :value="e.id"
                :label="`${e.realName}（${e.specialty}）`" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题标题" prop="title">
            <el-input v-model.trim="form.title" maxlength="100" placeholder="一句话概括问题" />
          </el-form-item>
          <el-form-item label="问题描述" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="5" maxlength="500" show-word-limit
              placeholder="详细描述你的问题，方便专家准确回答" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">提交提问</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.ask-page {
  padding-bottom: 40px;
}

.ask-card {
  max-width: 720px;
}
</style>
