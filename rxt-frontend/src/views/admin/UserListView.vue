<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, changeUserStatus } from '@/api/admin'
import { authState } from '@/utils/auth'
import {
  ROLE_OPTIONS,
  roleLabel,
  roleTagType,
  userStatusLabel,
  userStatusTagType,
  USER_STATUS_FILTER_OPTIONS
} from '@/utils/format'

/**
 * 用户管理（SRS 表38 / 表59-1、2）。
 *
 * <p>两个接口：
 * <pre>
 *   GET /api/admin/user/list        分页 + 角色/状态/关键词筛选
 *   PUT /api/admin/user/{id}/status 启用(1) 或 禁用(3)
 * </pre>
 *
 * <p><b>本页有个必须在前端拦住的规则：管理员不能禁用自己。</b>
 * 后端也会返回 1001「不能禁用自己的账号」，但前端把按钮直接置灰体验更好。
 * 这不是重复校验 —— 是「把服务端规则提前到交互层」，
 * 让用户在点击之前就知道这个操作不可用。
 *
 * <p>另外注意：禁用后该账号<b>无法登录</b>（表38 原文），这是有业务含义的，
 * 所以确认弹窗里的文案要说清楚后果，而不是干巴巴地问"确定吗"。
 */
const loading = ref(false)
const tableData = ref([])

const query = reactive({
  page: 1,
  size: 10,
  role: '',
  status: null,
  keyword: ''
})

const pageInfo = reactive({
  total: 0,
  pages: 0
})

const currentUserId = computed(() => authState.user?.id)

async function fetchList() {
  loading.value = true
  try {
    // 空值不要发给后端 —— @RequestParam(required=false) 收到空串会被当成
    // "要筛 status=''"，结果一条都查不出来。
    // 这里用 Number.isInteger 判断而不是 !== null：el-select 的 clearable
    // 在不同版本里可能把值置成 null / undefined / ''，逐个判断容易漏。
    const params = {
      page: query.page,
      size: query.size
    }
    if (query.role) params.role = query.role
    if (Number.isInteger(query.status)) params.status = query.status
    if (query.keyword) params.keyword = query.keyword

    const data = await listUsers(params)
    tableData.value = data.records || []
    pageInfo.total = data.total || 0
    pageInfo.pages = data.pages || 0
  } catch (e) {
    tableData.value = []
    pageInfo.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.role = ''
  query.status = null
  query.keyword = ''
  query.page = 1
  fetchList()
}

function handlePageChange(p) {
  query.page = p
  fetchList()
}

function handleSizeChange(s) {
  query.size = s
  query.page = 1
  fetchList()
}

/** 当前行是不是操作者自己 */
function isSelf(row) {
  return row.id === currentUserId.value
}

/** 已经禁用的行，按钮应该是"启用"，反之亦然 */
function isDisabled(row) {
  return row.status === 3
}

async function toggleStatus(row) {
  const targetStatus = isDisabled(row) ? 1 : 3
  const actionText = isDisabled(row) ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(
      isDisabled(row)
        ? `启用后，「${row.name || row.phone}」将可以正常登录系统。`
        : `禁用后，「${row.name || row.phone}」将无法登录系统，其已签发的登录凭证也会在过期前保持有效。确定继续？`,
      `${actionText}账号`,
      {
        confirmButtonText: `确定${actionText}`,
        cancelButtonText: '取消',
        type: isDisabled(row) ? 'info' : 'warning'
      }
    )
  } catch {
    return
  }

  try {
    await changeUserStatus(row.id, targetStatus)
    ElMessage.success(`已${actionText}该账号`)
    // 局部更新而不是整表刷新：避免分页位置跳动，体验更稳
    row.status = targetStatus
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">
          查看平台全部注册账号，按角色、状态或关键词筛选，并启用 / 禁用账号。
          禁用后该账号无法登录。
        </p>
      </div>
      <el-button :loading="loading" @click="fetchList">
        <el-icon><Refresh /></el-icon>
        <span style="margin-left: 4px">刷新</span>
      </el-button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="角色">
          <el-select
            v-model="query.role"
            placeholder="全部角色"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in ROLE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in USER_STATUS_FILTER_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="手机号 / 姓名 / 邮箱"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />

        <el-table-column prop="phone" label="手机号" width="130" />

        <el-table-column label="姓名" min-width="110">
          <template #default="{ row }">
            {{ row.name || '—' }}
          </template>
        </el-table-column>

        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="light">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="userStatusTagType(row.status)" size="small">
              {{ userStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="所在地" min-width="150">
          <template #default="{ row }">
            <span v-if="row.province || row.city">
              {{ [row.province, row.city, row.district].filter(Boolean).join(' / ') }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="邮箱" min-width="170">
          <template #default="{ row }">
            <span v-if="row.email">{{ row.email }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="165">
          <template #default="{ row }">
            <span class="mono">{{ row.createTime || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="最近登录" width="165">
          <template #default="{ row }">
            <span v-if="row.lastLoginAt" class="mono">{{ row.lastLoginAt }}</span>
            <span v-else class="muted">从未登录</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 自己那一行不允许禁用 -->
            <el-tooltip
              v-if="isSelf(row)"
              content="不能禁用自己的账号"
              placement="top"
            >
              <el-button size="small" disabled>禁用</el-button>
            </el-tooltip>

            <el-button
              v-else
              size="small"
              :type="isDisabled(row) ? 'success' : 'danger'"
              plain
              @click="toggleStatus(row)"
            >
              {{ isDisabled(row) ? '启用' : '禁用' }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的用户" :image-size="90" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="pageInfo.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: #b0b5bd;
}

.mono {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #4b5563;
}
</style>
