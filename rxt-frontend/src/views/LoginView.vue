<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/user'
import { setAuth } from '@/utils/auth'
import { homePath } from '@/router'

/**
 * 登录页。
 *
 * <p>业务规则（对齐 SRS 表12）：
 * <ul>
 *   <li>手机号 + 密码登录，成功后拿到 JWT，有效期 24 小时；</li>
 *   <li>连续输错 5 次锁定账号 30 分钟（后端负责，前端只负责把 1005 的提示展示出来）；</li>
 *   <li>待审核 / 已拒绝 / 已禁用的账号会返回 1003，前端不需要自己判断 status。</li>
 * </ul>
 * 也就是说，这个页面**没有业务逻辑**，只做「收集输入 → 调接口 → 存登录态 → 跳转」。
 */
const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  phone: '',
  password: ''
})

/**
 * 前端校验规则。
 * 注意这些和后端 DTO 上的注解是**同一套规则的重复实现**，这不是冗余：
 * 前端这份是为了「不必要地发请求也能立刻提示」，是体验；
 * 后端那份才是「挡得住绕过前端的请求」，是安全。两边都要有。
 */
const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return

  // validate() 校验失败会 reject，必须 catch，否则控制台报未处理的 Promise
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ phone: form.phone, password: form.password })

    // 存 Token 和用户信息（会同步写 localStorage，刷新页面不丢）
    setAuth(data.token, data.user)
    ElMessage.success(`欢迎回来，${data.user?.name || form.phone}`)

    // 登录前如果被守卫拦过，query.redirect 里存着原目标；没有就去角色对应的首页
    const redirect = route.query.redirect
    if (redirect) {
      // 用 replace 而不是 push：登录页不该留在历史记录里，否则点"后退"会回到登录页
      router.replace(redirect)
    } else {
      router.replace(homePath())
    }
  } catch (e) {
    // 错误提示已经在 axios 拦截器里统一做过了，这里不用再弹一次，
    // 只需要清掉密码输入框（这是登录页的惯例，防止旁人看到残留的密码）
    form.password = ''
  } finally {
    loading.value = false
  }
}

/** 回车直接提交，省一次点鼠标 */
function onEnter() {
  handleLogin()
}

/** 一键填入测试账号，方便验收和演示 */
function fillAccount(phone) {
  form.phone = phone
  form.password = '123456'
}
</script>

<template>
  <div class="auth-wrapper">
    <div class="auth-brand-panel">
      <div class="panel-brand"><span class="panel-dot" />融销通</div>
      <h1 class="panel-title">让农产品<br />卖得更好</h1>
      <p class="panel-sub">农产品交易 · 融资服务 · 专家助力</p>
      <ul class="panel-features">
        <li>农户开店卖货，直达买家</li>
        <li>联保小组互助，银行融资</li>
        <li>农业专家在线答疑指导</li>
      </ul>
    </div>

    <div class="auth-form-panel">
      <div class="auth-card">
        <div class="auth-brand">
          <h1 class="auth-brand-title">欢迎回来</h1>
          <p class="auth-brand-sub">登录融销通账号</p>
        </div>

        <div class="tip-box">
          <div style="margin-bottom: 6px; font-weight: 600">测试账号（密码统一 123456）</div>
          <div class="tip-accounts">
            <el-button size="small" text type="primary" @click="fillAccount('13900000000')">管理员</el-button>
            <el-button size="small" text type="primary" @click="fillAccount('13900000004')">农户</el-button>
            <el-button size="small" text type="primary" @click="fillAccount('13900000003')">待审核专家</el-button>
          </div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="onEnter"
        >
          <el-form-item label="手机号" prop="phone">
            <el-input v-model.trim="form.phone" placeholder="请输入 11 位手机号" maxlength="11">
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="auth-footer">
          还没有账号？
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tip-accounts {
  display: flex;
  gap: 4px;
}

.link {
  color: #16a34a;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}
</style>
