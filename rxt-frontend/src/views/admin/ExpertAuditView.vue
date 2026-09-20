<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, auditExpert } from '@/api/admin'
import { AUDIT_APPROVED, AUDIT_REJECTED, roleLabel } from '@/utils/format'

/**
 * 专家账号审核（SRS 表39 / 表59-3）。
 *
 * <p>接口：
 * <pre>
 *   取待审核列表：GET /api/admin/user/list?role=expert&amp;status=0   ← 复用用户列表接口
 *   审核：        PUT /api/admin/expert/{id}/audit                  ← id 是 t_user.id
 * </pre>
 *
 * <p><b>两个极易踩坑的点，代码里都做了处理：</b>
 *
 * <p><b>1. 路径里的 id 是 t_user.id，不是 t_expert.id。</b>
 * 待审核状态下 t_expert 表里<b>还没有这一行</b>（它是审核通过后才写入的，
 * 这是表39 的原文规定）。所以这里只能传用户 id ——
 * 也就是列表接口返回的 {@code row.id}。如果传了 t_expert.id，
 * 大部分情况下会 1008「资源不存在」，而审核通过过的专家会审核错人。
 *
 * <p><b>2. 专家的申请材料在 t_user.extend_json 里，不在接口的顶层字段。</b>
 * 因为 UserVO 是面向"所有角色"的通用结构（农户、银行、管理员共用），
 * 不可能为专家单开 realName/title/org/domain 四个字段。
 * 所以这些差异字段被序列化进 extend_json 存放。
 * 前端要展示就得自己解析 —— 见 {@link parseExtend}。
 *
 * <p><b>3. 审核结果不可撤回。</b>表39 明确规定，所以通过/拒绝的确认弹窗
 * 必须把这句话讲清楚，避免管理员随手点。
 */
const loading = ref(false)
const tableData = ref([])

const query = reactive({
  page: 1,
  size: 10,
  status: 0 // 默认只看待审核，这是这个页面的主用途
})

const pageInfo = reactive({ total: 0 })

/** 审核弹窗状态 */
const dialog = reactive({
  visible: false,
  submitting: false,
  row: null,
  form: {
    status: AUDIT_APPROVED,
    remark: ''
  }
})

const pendingCount = computed(() => (query.status === 0 ? pageInfo.total : 0))

/**
 * 解析 extend_json。
 *
 * <p>后端存的是 JSON 字符串，但**任何环节都可能出问题**：
 * 字段可能是 null（老数据）、可能是空字符串、也可能是被手工改坏的内容。
 * 直接 JSON.parse 一旦抛异常，整个页面就白屏了。
 * 所以这里必须 try/catch，失败时返回空对象，让界面退化成"资料未填写"。
 */
function parseExtend(row) {
  const raw = row?.extendJson
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size, role: 'expert' }
    // status 为 null 表示"不限"，此时不传该参数
    if (Number.isInteger(query.status)) params.status = query.status

    const data = await listUsers(params)

    // 在数据到手时一次性解析 extendJson，挂到 _extend 上。
    // 不放在模板里 parseExtend(row) 的原因：模板每次重渲染都会重新执行，
    // 而 JSON.parse 是有成本的。这里解析一次，后面直接用。
    // （下划线前缀是约定：这是前端为了渲染附加的字段，不是后端返回的）
    tableData.value = (data.records || []).map((row) => ({
      ...row,
      _extend: parseExtend(row)
    }))
    pageInfo.total = data.total || 0
  } catch (e) {
    tableData.value = []
    pageInfo.total = 0
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  query.page = 1
  fetchList()
}

function handlePageChange(p) {
  query.page = p
  fetchList()
}

function openAudit(row, status) {
  dialog.row = row
  dialog.form.status = status
  dialog.form.remark = ''
  dialog.visible = true
}

async function submitAudit() {
  const { status, remark } = dialog.form

  // 前端前置校验：拒绝必须写理由。
  // 后端 AuditDTO 也会校验（返回 1001），但那要发一次请求才知道；
  // 在本地拦住能少一次往返，也更快给出反馈。
  if (status === AUDIT_REJECTED && !remark.trim()) {
    ElMessage.warning('拒绝时必须填写理由，申请人需要知道被拒的原因')
    return
  }

  dialog.submitting = true
  try {
    await auditExpert(dialog.row.id, {
      status,
      remark: remark.trim() || null
    })
    ElMessage.success(status === AUDIT_APPROVED ? '已通过该专家的申请' : '已拒绝该专家的申请')
    dialog.visible = false
    // 审核完列表会少一条，需要重新拉取
    fetchList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    dialog.submitting = false
  }
}

/** 打开申请人的完整资料弹窗（材料较多，表格里放不下） */
const detail = reactive({ visible: false, row: null, extend: {} })

function openDetail(row) {
  detail.row = row
  detail.extend = row._extend || parseExtend(row)
  detail.visible = true
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">专家审核</h2>
        <p class="page-desc">
          审核专家的注册申请。通过后账号状态变为「正常」并写入专家档案；
          拒绝需填写理由。审核结果不可撤回。
        </p>
      </div>
      <el-button :loading="loading" @click="fetchList">
        <el-icon><Refresh /></el-icon>
        <span style="margin-left: 4px">刷新</span>
      </el-button>
    </div>

    <el-alert
      v-if="pendingCount > 0"
      type="warning"
      :closable="false"
      show-icon
      :title="`当前有 ${pendingCount} 位专家在等待审核`"
      style="margin-bottom: 16px"
    />

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="审核状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 160px"
            @change="handleStatusChange"
          >
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <span class="filter-hint">
            共 {{ pageInfo.total }} 条记录
          </span>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="用户ID" width="80" align="center" />

        <el-table-column label="申请人" min-width="150">
          <template #default="{ row }">
            <div class="applicant">
              <el-avatar :size="30" class="applicant-avatar">
                {{ (row.name || row.phone).slice(0, 1) }}
              </el-avatar>
              <div>
                <div class="applicant-name">{{ row.name || '未填写' }}</div>
                <div class="applicant-phone">{{ row.phone }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="专业领域" min-width="140">
          <template #default="{ row }">
            <span v-if="row._extend.domain">{{ row._extend.domain }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="职称" width="130">
          <template #default="{ row }">
            <span v-if="row._extend.title">{{ row._extend.title }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="所属机构" min-width="180">
          <template #default="{ row }">
            <span v-if="row._extend.org">{{ row._extend.org }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 1" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else type="info" size="small">已禁用</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="申请时间" width="165">
          <template #default="{ row }">
            <span class="mono">{{ row.createTime || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <div class="op-actions">
              <el-button size="small" text type="primary" @click="openDetail(row)">
                查看资料
              </el-button>

              <template v-if="row.status === 0">
                <el-button size="small" type="success" plain @click="openAudit(row, AUDIT_APPROVED)">
                  通过
                </el-button>
                <el-button size="small" type="danger" plain @click="openAudit(row, AUDIT_REJECTED)">
                  拒绝
                </el-button>
              </template>

              <el-tag v-else size="small" type="info" effect="plain">
                已审核
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            :description="query.status === 0 ? '暂无待审核的专家申请' : '没有符合条件的记录'"
            :image-size="90"
          />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="pageInfo.total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- ==================== 申请资料详情 ==================== -->
    <el-dialog v-model="detail.visible" title="专家申请资料" width="620px">
      <el-descriptions v-if="detail.row" :column="1" border>
        <el-descriptions-item label="用户 ID">{{ detail.row.id }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.row.phone }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.row.name || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="专业领域">
          {{ detail.extend.domain || '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="职称">
          {{ detail.extend.title || '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="所属机构">
          {{ detail.extend.org || '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="个人简介">
          <div class="intro-text">{{ detail.extend.intro || '未填写' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="资质证明">
          <el-link
            v-if="detail.extend.certUrl"
            :href="detail.extend.certUrl"
            target="_blank"
            type="primary"
          >
            查看证明材料
          </el-link>
          <span v-else class="muted">未上传</span>
        </el-descriptions-item>
        <el-descriptions-item label="联系邮箱">
          {{ detail.row.email || '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="所在地">
          {{ [detail.row.province, detail.row.city, detail.row.district].filter(Boolean).join(' / ') || '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detail.row.createTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.row.auditRemark" label="审核意见">
          {{ detail.row.auditRemark }}
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 审核弹窗 ==================== -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.form.status === AUDIT_APPROVED ? '通过专家申请' : '拒绝专家申请'"
      width="520px"
    >
      <el-alert
        v-if="dialog.row"
        :type="dialog.form.status === AUDIT_APPROVED ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="`申请人：${dialog.row.name || dialog.row.phone}`"
        :description="
          dialog.form.status === AUDIT_APPROVED
            ? '通过后该账号立即激活，可以登录并发布专业文章。审核结果不可撤回。'
            : '拒绝后该账号将无法登录。审核结果不可撤回，请谨慎操作。'
        "
        style="margin-bottom: 18px"
      />

      <el-form label-width="80px">
        <el-form-item label="审核意见">
          <el-input
            v-model="dialog.form.remark"
            type="textarea"
            :rows="4"
            :placeholder="
              dialog.form.status === AUDIT_REJECTED
                ? '必填：请说明拒绝原因，申请人会看到此内容'
                : '选填：可补充通过说明'
            "
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button
          :type="dialog.form.status === AUDIT_APPROVED ? 'success' : 'danger'"
          :loading="dialog.submitting"
          @click="submitAudit"
        >
          {{ dialog.form.status === AUDIT_APPROVED ? '确认通过' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.op-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
}

.op-actions .el-button + .el-button {
  margin-left: 0;
}

.applicant {
  display: flex;
  gap: 10px;
  align-items: center;
}

.applicant-avatar {
  background: #16a34a;
  font-size: 13px;
  flex-shrink: 0;
}

.applicant-name {
  font-weight: 600;
  color: #1f2329;
}

.applicant-phone {
  font-size: 12px;
  color: #8a9099;
}

.muted {
  color: #b0b5bd;
}

.mono {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #4b5563;
}

.intro-text {
  line-height: 1.7;
  color: #4b5563;
}

.filter-hint {
  font-size: 13px;
  color: #8a9099;
}
</style>
