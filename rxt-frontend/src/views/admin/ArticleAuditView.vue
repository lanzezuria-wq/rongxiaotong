<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listArticles, auditArticle } from '@/api/admin'
import {
  AUDIT_APPROVED,
  AUDIT_REJECTED,
  articleStatusLabel,
  articleStatusTagType,
  ARTICLE_STATUS_OPTIONS,
  roleLabel
} from '@/utils/format'

/**
 * 文章审核（SRS 表40 / 表58）。
 *
 * <p><b>这个页面对应的接口是全项目最特殊的一个，值得单独看：</b>
 * <pre>
 *   GET /api/article/list          文章列表（游客可读，公开接口）
 *   PUT /api/article/{id}/audit    审核文章（仅管理员）  ← 路径不在 /api/admin 下！
 * </pre>
 *
 * <p>P1 的 M7 要交付 8 个接口，但 SRS 表59（系统管理模块）里只有 7 个。
 * 第 8 个「文章审核」藏在<b>表58（专家助力模块）</b>里 ——
 * 虽然由管理员使用、属于系统管理职能，但按 URL 归属被划到了专家模块下。
 * 这带来两个后果：
 * <ol>
 *   <li>后端必须在 SecurityConfig 里为它<b>单独</b>配一条管理员规则，
 *       {@code /api/admin/**} 那条覆盖不到；</li>
 *   <li>它和 {@code GET /api/article/list} 都是表58 的范围，
 *       <b>需要和负责 P4 的同学确认归属</b>，否则会撞车。</li>
 * </ol>
 *
 * <p>业务规则（表40）：农户发布的文章需审核，专家可以直接发布。
 * 所以这个列表里既有农户的投稿，也可能有已经发布成功的专家文章（status=1）。
 */
const loading = ref(false)
const tableData = ref([])

const query = reactive({
  page: 1,
  size: 10,
  status: 0, // 默认只看待审核 —— 这是本页的主用途
  keyword: ''
})

const pageInfo = reactive({ total: 0 })

const pendingCount = computed(() => (query.status === 0 ? pageInfo.total : 0))

/** 审核弹窗 */
const dialog = reactive({
  visible: false,
  submitting: false,
  row: null,
  form: { status: AUDIT_APPROVED, remark: '' }
})

/** 正文预览弹窗 */
const preview = reactive({ visible: false, row: null })

async function fetchList() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (Number.isInteger(query.status)) params.status = query.status
    if (query.keyword) params.keyword = query.keyword

    const data = await listArticles(params)
    tableData.value = data.records || []
    pageInfo.total = data.total || 0
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
  query.status = 0
  query.keyword = ''
  query.page = 1
  fetchList()
}

function handlePageChange(p) {
  query.page = p
  fetchList()
}

function openPreview(row) {
  preview.row = row
  preview.visible = true
}

function openAudit(row, status) {
  // 正文预览弹窗的底部也有「通过 / 拒绝」按钮。
  // 从预览里点进来时要把预览关掉，否则两个弹窗会叠在一起，
  // 提交完审核后预览还留在屏幕上，状态看起来是矛盾的。
  preview.visible = false

  dialog.row = row
  dialog.form.status = status
  dialog.form.remark = ''
  dialog.visible = true
}

async function submitAudit() {
  const { status, remark } = dialog.form

  if (status === AUDIT_REJECTED && !remark.trim()) {
    ElMessage.warning('拒绝时必须填写理由，作者需要知道未通过的原因')
    return
  }

  dialog.submitting = true
  try {
    await auditArticle(dialog.row.id, {
      status,
      remark: remark.trim() || null
    })
    ElMessage.success(status === AUDIT_APPROVED ? '文章已通过审核并发布' : '已拒绝该文章')
    dialog.visible = false
    fetchList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    dialog.submitting = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">文章审核</h2>
        <p class="page-desc">
          审核农户发布的知识文章。通过后文章对公众可见；拒绝需填写理由。
          专家发布的文章无需审核。
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
      :title="`当前有 ${pendingCount} 篇文章等待审核`"
      style="margin-bottom: 16px"
    />

    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in ARTICLE_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="标题关键词"
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

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />

        <el-table-column label="标题" min-width="240">
          <template #default="{ row }">
            <el-button link type="primary" class="title-link" @click="openPreview(row)">
              {{ row.title }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" effect="plain">{{ row.category }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="作者" min-width="140">
          <template #default="{ row }">
            <div class="author-cell">
              <span>{{ row.authorName || '匿名' }}</span>
              <el-tag
                v-if="row.authorRole"
                size="small"
                :type="row.authorRole === 'expert' ? 'success' : 'info'"
                effect="plain"
              >
                {{ roleLabel(row.authorRole) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="阅读量" width="90" align="center">
          <template #default="{ row }">{{ row.viewCount ?? 0 }}</template>
        </el-table-column>

        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="articleStatusTagType(row.status)" size="small">
              {{ articleStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="提交时间" width="165">
          <template #default="{ row }">
            <span class="mono">{{ row.createTime || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openPreview(row)">
              查看正文
            </el-button>

            <template v-if="row.status === 0">
              <el-button size="small" type="success" plain @click="openAudit(row, AUDIT_APPROVED)">
                通过
              </el-button>
              <el-button size="small" type="danger" plain @click="openAudit(row, AUDIT_REJECTED)">
                拒绝
              </el-button>
            </template>

            <el-tag v-else size="small" type="info" effect="plain" style="margin-left: 4px">
              已处理
            </el-tag>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            :description="query.status === 0 ? '暂无待审核文章' : '没有符合条件的文章'"
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

    <!-- ==================== 正文预览 ==================== -->
    <el-dialog v-model="preview.visible" title="文章正文" width="720px" top="6vh">
      <template v-if="preview.row">
        <h3 class="preview-title">{{ preview.row.title }}</h3>

        <div class="preview-meta">
          <span>作者：{{ preview.row.authorName || '匿名' }}</span>
          <el-divider direction="vertical" />
          <span>分类：{{ preview.row.category || '未分类' }}</span>
          <el-divider direction="vertical" />
          <span>提交：{{ preview.row.createTime }}</span>
          <el-divider direction="vertical" />
          <el-tag :type="articleStatusTagType(preview.row.status)" size="small">
            {{ articleStatusLabel(preview.row.status) }}
          </el-tag>
        </div>

        <div v-if="preview.row.tags" class="preview-tags">
          <el-tag
            v-for="t in String(preview.row.tags).split(',').filter(Boolean)"
            :key="t"
            size="small"
            effect="plain"
          >
            {{ t.trim() }}
          </el-tag>
        </div>

        <el-divider />

        <!--
          正文按纯文本渲染（white-space: pre-wrap 保留换行）。
          这里刻意不用 v-html —— 审核页会展示用户提交的内容，
          用 v-html 等于让投稿者能往管理员页面上注入脚本（XSS）。
          富文本展示应该由后端做净化后返回可信 HTML，而不是前端直接渲染原文。
        -->
        <div class="preview-content">{{ preview.row.content || '（正文为空）' }}</div>

        <el-alert
          v-if="preview.row.auditRemark"
          type="info"
          :closable="false"
          show-icon
          :title="`审核意见：${preview.row.auditRemark}`"
          style="margin-top: 18px"
        />
      </template>

      <template #footer>
        <el-button @click="preview.visible = false">关闭</el-button>
        <template v-if="preview.row && preview.row.status === 0">
          <el-button type="danger" plain @click="openAudit(preview.row, AUDIT_REJECTED)">
            拒绝
          </el-button>
          <el-button type="success" @click="openAudit(preview.row, AUDIT_APPROVED)">
            通过并发布
          </el-button>
        </template>
      </template>
    </el-dialog>

    <!-- ==================== 审核弹窗 ==================== -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.form.status === AUDIT_APPROVED ? '通过文章审核' : '拒绝文章'"
      width="520px"
    >
      <el-alert
        v-if="dialog.row"
        :type="dialog.form.status === AUDIT_APPROVED ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="dialog.row.title"
        :description="
          dialog.form.status === AUDIT_APPROVED
            ? '通过后文章立即对公众可见。审核结果不可重复操作。'
            : '拒绝后文章不会对外展示。请说明理由，作者可以看到。'
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
                ? '必填：请说明未通过的原因'
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
.title-link {
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  white-space: normal;
}

.author-cell {
  display: flex;
  gap: 6px;
  align-items: center;
}

.muted {
  color: #b0b5bd;
}

.mono {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #4b5563;
}

.preview-title {
  margin: 0 0 12px;
  font-size: 19px;
  font-weight: 600;
  color: #1f2329;
}

.preview-meta {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #8a9099;
}

.preview-tags {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

.preview-content {
  max-height: 46vh;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.9;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
