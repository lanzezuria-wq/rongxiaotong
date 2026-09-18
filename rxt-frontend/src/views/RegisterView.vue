<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { register } from '@/api/user'
import { setAuth } from '@/utils/auth'
import { homePath } from '@/router'
import { ROLE_OPTIONS } from '@/utils/format'

/**
 * 注册页。
 *
 * <p><b>这个页面有一个非典型分支，是理解业务的关键：</b>
 * 注册不是「一种结果」，而是两种，取决于所选角色（SRS 表11）：
 * <pre>
 *   农户 / 买家  → status = 1（正常），后端直接返回 Token，等于注册即登录，跳首页
 *   专家         → status = 0（待审核），token = null，必须提示"等待审核"，跳登录页
 * </pre>
 * 前端不判断这个规则，而是读响应里的 {@code needAudit} 字段 ——
 * 规则由后端一处决定，前端只负责按结果分流。以后如果运营规则改成
 * "专家免审核"，后端改一处即可，前端不用动。
 */
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  phone: '',
  password: '',
  confirmPassword: '',
  role: 'farmer',
  verifyCode: ''
})

/** 自助注册只开放这两个角色。bank / admin 只能由管理员创建 */
const roleOptions = ROLE_OPTIONS.filter((r) => r.value === 'farmer' || r.value === 'expert')

/** 自定义校验器：确认密码必须和密码一致 */
function validateConfirm(rule, value, callback) {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  verifyCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ]
}

const isExpert = computed(() => form.role === 'expert')

async function handleRegister() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 注意：不要把 confirmPassword 传给后端 —— DTO 里没有这个字段，
    // 传了虽然不报错（Jackson 默认忽略未知字段），但属于无意义的噪音。
    const data = await register({
      phone: form.phone,
      password: form.password,
      role: form.role,
      verifyCode: form.verifyCode
    })

    if (data.needAudit) {
      // 专家分支：没有 Token，不能进系统，引导回登录页
      await ElMessageBox.alert(
        '您的专家账号已提交，请等待平台管理员审核。审核通过后即可登录使用。',
        '注册成功',
        { confirmButtonText: '知道了', type: 'success' }
      )
      router.replace('/login')
      return
    }

    // 农户 / 买家分支：后端已经给了 Token，直接登录进系统
    setAuth(data.token, data.user)
    ElMessage.success('注册成功，已自动登录')
    router.replace(homePath())
  } catch (e) {
    // 提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrapper">
    <div class="auth-card" style="width: 460px">
      <div class="auth-brand">
        <h1 class="auth-brand-title">注册账号</h1>
        <p class="auth-brand-sub">加入融销通，连接农业与金融</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="form.phone" placeholder="请输入 11 位手机号" maxlength="11">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6-20 位，建议字母加数字"
            show-password
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="注册角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio v-for="opt in roleOptions" :key="opt.value" :value="opt.value" border>
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <div class="role-hint">
            <template v-if="isExpert">
              <el-icon><InfoFilled /></el-icon>
              专家账号需平台管理员审核，审核通过后才能登录
            </template>
            <template v-else>
              <el-icon><InfoFilled /></el-icon>
              农户与买家共用此角色，注册后可直接登录
            </template>
          </div>
        </el-form-item>

        <el-form-item label="短信验证码" prop="verifyCode">
          <el-input v-model.trim="form.verifyCode" placeholder="演示环境固定为 111111" maxlength="6">
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <router-link to="/login" class="link">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.role-hint {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #d97706;
}

.link {
  color: #16a34a;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}
</style>
