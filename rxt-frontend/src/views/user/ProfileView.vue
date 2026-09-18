<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInfo, updateInfo } from '@/api/user'
import { setUser } from '@/utils/auth'
import { roleLabel, userStatusLabel, userStatusTagType } from '@/utils/format'

/**
 * 个人中心 —— 查看 + 编辑自己的资料（SRS 表14）。
 *
 * <p><b>本页最重要的设计点：哪些字段可改、哪些不可改。</b>
 * 可改：姓名、邮箱、省市区、头像
 * 不可改：手机号、角色、状态
 *
 * <p>为什么会这样？看两个依据：
 * <ol>
 *   <li>SRS 表14 的「输入」只列了这五类字段；</li>
 *   <li>手机号是登录凭证，能随便改就等于能转移账号；
 *       角色和状态是权限相关字段，能自己改就意味着能自提管理员。</li>
 * </ol>
 * 实现上不是靠前端「把输入框设为 disabled」来防的——那只能防误操作，
 * 防不住改请求体。<b>真正的防线是 UpdateInfoDTO 里根本没有这三个字段</b>，
 * 传了也会被 Jackson 忽略。前端这里置灰只是为了告诉用户"这个改不了"。
 */
const loading = ref(false)
const saving = ref(false)
const formRef = ref(null)

/** 只读区展示用（来自最新的服务端数据） */
const profile = ref(null)

/** 可编辑区 */
const form = reactive({
  name: '',
  email: '',
  province: '',
  city: '',
  district: '',
  avatar: ''
})

const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 20, message: '姓名不超过 20 个字符', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const displayRole = computed(() => roleLabel(profile.value?.role))
const statusTag = computed(() => userStatusTagType(profile.value?.status))
const statusText = computed(() => userStatusLabel(profile.value?.status))

/** 拉取最新资料并回填两个区 */
async function load() {
  loading.value = true
  try {
    const data = await getInfo()
    profile.value = data

    // 回填可编辑字段。注意用 ?? '' 兜住 null —— el-input 拿到 null 会报警告
    form.name = data.name ?? ''
    form.email = data.email ?? ''
    form.province = data.province ?? ''
    form.city = data.city ?? ''
    form.district = data.district ?? ''
    form.avatar = data.avatar ?? ''

    // 顺手刷新全局登录态里的用户信息，让顶部导航的名字跟着变
    setUser(data)
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    // 部分更新：这里把所有可编辑字段都发过去。
    // 后端的语义是「只更新非 null 的字段」，传空字符串会真的把字段置空 ——
    // 这正是我们想要的：用户清空邮箱就是"不想填邮箱"。
    const data = await updateInfo({ ...form })
    profile.value = data
    setUser(data)
    ElMessage.success('资料已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function handleReset() {
  if (profile.value) {
    form.name = profile.value.name ?? ''
    form.email = profile.value.email ?? ''
    form.province = profile.value.province ?? ''
    form.city = profile.value.city ?? ''
    form.district = profile.value.district ?? ''
    form.avatar = profile.value.avatar ?? ''
  }
  formRef.value?.clearValidate()
  ElMessage.info('已还原为上次保存的内容')
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">个人中心</h2>
        <p class="page-desc">
          查看账号信息，并维护姓名、联系方式与所在地区。手机号作为登录凭证不可修改。
        </p>
      </div>
      <el-button :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>
        <span style="margin-left: 4px">刷新</span>
      </el-button>
    </div>

    <el-row :gutter="16">
      <!-- 左：账号概览（只读） -->
      <el-col :xs="24" :md="8">
        <el-card v-loading="loading" shadow="never" class="account-card">
          <div class="account-head">
            <el-avatar :size="64" class="account-avatar">
              {{ (profile?.name || profile?.phone || 'U').slice(0, 1) }}
            </el-avatar>
            <div class="account-name">{{ profile?.name || '未设置姓名' }}</div>
            <el-tag :type="statusTag" size="small">{{ statusText }}</el-tag>
          </div>

          <el-divider />

          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="用户 ID">{{ profile?.id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ profile?.phone ?? '-' }}
              <el-tag size="small" type="info" effect="plain" style="margin-left: 6px">
                不可修改
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="角色">{{ displayRole }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ profile?.createTime ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ profile?.lastLoginAt ?? '暂无记录' }}</el-descriptions-item>
            <el-descriptions-item v-if="profile?.auditRemark" label="审核意见">
              {{ profile.auditRemark }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 右：资料编辑 -->
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">编辑资料</span>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="80px"
            v-loading="loading"
          >
            <el-form-item label="姓名" prop="name">
              <el-input v-model.trim="form.name" placeholder="请输入真实姓名" maxlength="20" />
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="form.email" placeholder="用于接收平台通知，可留空" />
            </el-form-item>

            <el-form-item label="所在地区">
              <div class="region-row">
                <el-input v-model.trim="form.province" placeholder="省 / 直辖市" />
                <el-input v-model.trim="form.city" placeholder="市" />
                <el-input v-model.trim="form.district" placeholder="区 / 县" />
              </div>
            </el-form-item>

            <el-form-item label="头像地址">
              <el-input v-model.trim="form.avatar" placeholder="图片 URL，可留空使用默认头像" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
              <el-button @click="handleReset">还原</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.account-card {
  height: 100%;
}

.account-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.account-avatar {
  background: #16a34a;
  font-size: 24px;
}

.account-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.region-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
