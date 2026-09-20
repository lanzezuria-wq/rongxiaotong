<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applyFinancing, joinGroup, createGroup } from '@/api/financing'
import { FUND_USAGE_OPTIONS, INDUSTRY_OPTIONS, LOAN_TERM_OPTIONS } from '@/utils/format'
import GroupCard from '@/components/GroupCard.vue'

/**
 * 融资申请页 —— 提交申请 + 引导加入匹配到的联保小组 / 发起新小组。
 *
 * <p>提交后后端会按「同行业 + 同省」匹配可加入的小组（matchedGroups），
 * 这里直接展示并引导「立即加入」，省得农户再去小组列表里翻。
 */
const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const joiningId = ref(null)
const creating = ref(false)

const form = reactive({
  amount: null,
  usage: '',
  industry: '',
  province: '',
  city: '',
  loanTerm: '',
  description: ''
})

/** 提交成功后的结果 { id, matchedGroups[] } */
const result = ref(null)

const rules = {
  amount: [{ required: true, message: '请输入申请金额', trigger: 'blur' }],
  usage: [{ required: true, message: '请选择资金用途', trigger: 'change' }],
  industry: [{ required: true, message: '请选择所属行业', trigger: 'change' }],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  loanTerm: [{ required: true, message: '请选择贷款期限', trigger: 'change' }],
  description: [{ required: true, message: '请填写用途说明', trigger: 'blur' }]
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.amount || form.amount < 10000 || form.amount > 500000) {
    ElMessage.warning('申请金额需在 1 万 ~ 50 万之间')
    return
  }
  submitting.value = true
  try {
    result.value = await applyFinancing({ ...form })
  } catch (e) {
    // 拦截器已统一提示（3001 已有进行中的申请等）
  } finally {
    submitting.value = false
  }
}

async function onJoin(group) {
  joiningId.value = group.id
  try {
    await joinGroup({ groupId: group.id, applyId: result.value.id })
    ElMessage.success('已加入联保小组')
    router.push('/financing/my')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    joiningId.value = null
  }
}

async function onCreateGroup() {
  creating.value = true
  try {
    await createGroup({ industry: form.industry, province: form.province, city: form.city })
    ElMessage.success('联保小组创建成功')
    router.push('/financing/my')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="apply-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">融资申请</h2>
          <p class="page-desc">申请金额 1 万 ~ 50 万，提交后加入 3~10 人联保小组共同担保</p>
        </div>
      </div>

      <!-- 阶段一：填写表单 -->
      <el-card v-if="!result" shadow="never" class="apply-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
          <el-form-item label="申请金额" prop="amount">
            <el-input-number v-model="form.amount" :min="10000" :max="500000" :step="10000" />
            <span class="hint">元（1 万 ~ 50 万）</span>
          </el-form-item>

          <el-form-item label="资金用途" prop="usage">
            <el-select v-model="form.usage" placeholder="请选择" style="width: 240px">
              <el-option v-for="o in FUND_USAGE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
            </el-select>
          </el-form-item>

          <el-form-item label="所属行业" prop="industry">
            <el-select v-model="form.industry" placeholder="请选择" style="width: 240px">
              <el-option v-for="o in INDUSTRY_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
            </el-select>
          </el-form-item>

          <el-form-item label="所在地区">
            <div class="region-row">
              <el-form-item prop="province" class="region-item"><el-input v-model.trim="form.province" placeholder="省" /></el-form-item>
              <el-form-item prop="city" class="region-item"><el-input v-model.trim="form.city" placeholder="市" /></el-form-item>
            </div>
          </el-form-item>

          <el-form-item label="贷款期限" prop="loanTerm">
            <el-select v-model="form.loanTerm" placeholder="请选择" style="width: 240px">
              <el-option v-for="o in LOAN_TERM_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
            </el-select>
          </el-form-item>

          <el-form-item label="用途说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit
              placeholder="说明资金用途和还款来源" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">提交申请</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 阶段二：申请结果 + 小组引导 -->
      <template v-else>
        <el-card shadow="never" class="apply-card">
          <el-result icon="success" title="申请提交成功"
            :sub-title="result.matchedGroups?.length
              ? `为您匹配到 ${result.matchedGroups.length} 个同行业同地区的联保小组`
              : '暂无匹配的联保小组，您可以自己发起一个'" />
        </el-card>

        <div v-if="result.matchedGroups?.length" class="group-section">
          <div class="section-head"><h3>匹配到的联保小组</h3></div>
          <div class="group-grid">
            <GroupCard v-for="g in result.matchedGroups" :key="g.id" :group="g">
              <el-button size="small" type="primary" :loading="joiningId === g.id" @click="onJoin(g)">
                立即加入
              </el-button>
            </GroupCard>
          </div>
        </div>

        <div class="result-actions">
          <el-button size="large" :loading="creating" @click="onCreateGroup">发起新小组</el-button>
          <el-button size="large" @click="$router.push('/financing/my')">查看我的融资</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.apply-page {
  padding-bottom: 40px;
}

.apply-card {
  max-width: 720px;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #8a9099;
}

.region-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.region-item {
  flex: 1;
  margin-bottom: 0;
}

.group-section {
  max-width: 900px;
  margin-top: 20px;
}

.section-head h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #1f2329;
}

.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.result-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
</style>
