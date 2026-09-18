<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword } from '@/api/user'
import { clearAuth } from '@/utils/auth'

/**
 * 修改密码（SRS 表13）。
 *
 * <p><b>本页有一个必须处理的后果，漏了会造成"改了密码但还在登录态"的错乱：</b>
 * JWT 是无状态的，服务端**无法主动作废**已经签发出去的 Token。
 * 也就是说，改完密码后，旧的 Token 在 24 小时有效期内仍然能用。
 * 所以正确做法是：修改成功后前端主动清掉本地 Token 并强制重新登录，
 * 顺带让用户用新密码验证一次。
 *
 * <p>业务规则（后端负责校验，前端重复一遍做即时提示）：
 * <ul>
 *   <li>原密码必须正确，否则 1001「原密码错误」；</li>
 *   <li>新密码不能与原密码相同；</li>
 *   <li>新密码长度 6-20 位。</li>
 * </ul>
 */
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function validateNewPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请输入新密码'))
  } else if (value === form.oldPassword) {
    callback(new Error('新密码不能与原密码相同'))
  } else {
    // 新密码变了要连带重新校验"确认密码"，否则会出现
    // "先填确认、再改新密码"导致两个字段不一致却都通过校验的情况
    if (form.confirmPassword) {
      formRef.value?.validateField('confirmPassword')
    }
    callback()
  }
}

function validateConfirm(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, validator: validateNewPassword, trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

/**
 * 简单的密码强度估算：只用来给用户一个正反馈，不参与校验。
 * 不引入 zxcvbn 之类的库 —— 本地估算够用，而且不增加包体积。
 */
const strength = computed(() => {
  const p = form.newPassword
  if (!p) return { level: 0, text: '', color: '#e5e7eb' }

  let score = 0
  if (p.length >= 8) score++
  if (p.length >= 12) score++
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++
  if (/\d/.test(p)) score++
  if (/[^a-zA-Z0-9]/.test(p)) score++

  if (p.length < 6) return { level: 1, text: '过短', color: '#dc2626' }
  if (score <= 2) return { level: 1, text: '弱', color: '#dc2626' }
  if (score === 3) return { level: 2, text: '中', color: '#d97706' }
  return { level: 3, text: '强', color: '#16a34a' }
})

async function handleSubmit() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })

    await ElMessageBox.alert(
      '密码修改成功。为保证账号安全，请使用新密码重新登录。',
      '修改成功',
      { confirmButtonText: '去登录', type: 'success' }
    )

    // 关键：主动清登录态。不清的话页面还是"已登录"，
    // 但用户以为自己是新密码登录的，状态会错乱。
    clearAuth()
    router.replace('/login')
  } catch (e) {
    // 拦截器已提示（原密码错误是 1001，会显示"原密码错误"）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">修改密码</h2>
        <p class="page-desc">
          为了账号安全，修改成功后需要重新登录。新密码不能与原密码相同。
        </p>
      </div>
    </div>

    <el-card shadow="never" style="max-width: 560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="当前登录状态在修改密码后即失效"
        description="Token 是无状态的，服务端无法远程作废已签发的凭证，因此本页会在修改成功后主动退出登录。"
        style="margin-bottom: 20px"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            placeholder="请输入当前使用的密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="6-20 位，建议字母加数字组合"
            show-password
          />
          <div v-if="form.newPassword" class="strength-row">
            <span class="strength-label">强度</span>
            <div class="strength-bars">
              <span
                v-for="i in 3"
                :key="i"
                class="strength-bar"
                :style="{
                  background: i <= strength.level ? strength.color : '#e5e7eb'
                }"
              />
            </div>
            <span class="strength-text" :style="{ color: strength.color }">
              {{ strength.text }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            确认修改
          </el-button>
          <el-button
            @click="
              () => {
                formRef?.resetFields()
              }
            "
          >
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.strength-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
}

.strength-label {
  color: #8a9099;
}

.strength-bars {
  display: flex;
  gap: 4px;
}

.strength-bar {
  width: 34px;
  height: 6px;
  border-radius: 3px;
  transition: background 0.2s;
}

.strength-text {
  font-weight: 600;
}
</style>
