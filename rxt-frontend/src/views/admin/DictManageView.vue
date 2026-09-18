<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDictByType, createDict, updateDict, deleteDict } from '@/api/admin'
import { DICT_TYPE_OPTIONS } from '@/utils/format'

/**
 * 数据字典管理（SRS 表41 / 表59-4~7，共 4 个接口）。
 *
 * <p><b>这个页面是全项目权限最特殊的一页，必须理解为什么：</b>
 * 同一个 Controller 下的 4 个接口权限不一样 ——
 * <pre>
 *   GET    /api/admin/dict/{type}   认证即可，不要求管理员
 *   POST   /api/admin/dict          仅管理员
 *   PUT    /api/admin/dict/{id}     仅管理员
 *   DELETE /api/admin/dict/{id}     仅管理员
 * </pre>
 * 查询之所以不限制角色，是因为前端的<b>所有下拉框</b>都依赖它：
 * 商品分类、资金用途、所属行业、贷款期限。如果只有管理员能查，
 * 那农户发布商品时连分类都选不出来。
 *
 * <p>所以本页会出现一个诡异现象：**非管理员用户也能打开这个页面的数据，
 * 但一点"新增"就会 403**。这不是 bug，是设计如此。
 * 路由守卫已经限制了整个 /admin 目录，普通用户看不到这个页面，
 * 后端的 GET 开放只是为了让其它页面的下拉框能用。
 *
 * <p><b>另外一个关键点：字典类型是白名单，不能自由输入。</b>
 * 后端 DictConst 用 Set 硬约束了四种类型，因为类型值被其他模块的业务表引用
 * （product_category → P2 商品表、fund_usage/industry/loan_term → P3 融资申请表）。
 * 自由输入会出现"数据库里有这个分类，但代码不认识它"的死数据。
 * 所以前端这里类型是下拉选择，不是文本框。
 */
const loading = ref(false)
const tableData = ref([])

/** 当前查看的类型。默认第一个，对应"产品分类" */
const activeType = ref(DICT_TYPE_OPTIONS[0].value)

const currentTypeName = computed(
  () => DICT_TYPE_OPTIONS.find((o) => o.value === activeType.value)?.label || ''
)

/** 新增 / 编辑弹窗 */
const dialog = reactive({
  visible: false,
  submitting: false,
  mode: 'create', // create | edit
  form: {
    id: null,
    type: '',
    code: '',
    name: '',
    sort: 0,
    enabled: true,
    remark: ''
  }
})

const formRef = ref(null)

const rules = {
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { max: 32, message: '编码不超过 32 个字符', trigger: 'blur' },
    {
      // 编码会被当作程序里的标识使用（还被拼进 URL），限制成安全的字符集
      pattern: /^[A-Za-z0-9_]+$/,
      message: '编码只能包含字母、数字和下划线',
      trigger: 'blur'
    }
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 50, message: '名称不超过 50 个字符', trigger: 'blur' }
  ],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const data = await listDictByType(activeType.value)
    tableData.value = data || []
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleTypeChange() {
  fetchList()
}

function resetForm() {
  dialog.form = {
    id: null,
    type: activeType.value,
    code: '',
    name: '',
    sort: 0,
    enabled: true,
    remark: ''
  }
  formRef.value?.clearValidate()
}

function openCreate() {
  dialog.mode = 'create'
  resetForm()
  // 排序值默认给当前最大值 +1，新增的项自然排在最后
  const maxSort = tableData.value.reduce((m, r) => Math.max(m, r.sort ?? 0), 0)
  dialog.form.sort = maxSort + 1
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.form = {
    id: row.id,
    type: row.type,
    code: row.code,
    name: row.name,
    sort: row.sort ?? 0,
    enabled: row.enabled !== false,
    remark: row.remark ?? ''
  }
  formRef.value?.clearValidate()
  dialog.visible = true
}

async function submit() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  dialog.submitting = true
  try {
    const payload = {
      type: dialog.form.type,
      code: dialog.form.code,
      name: dialog.form.name,
      sort: Number(dialog.form.sort) || 0,
      enabled: dialog.form.enabled,
      remark: dialog.form.remark || null
    }

    if (dialog.mode === 'create') {
      await createDict(payload)
      ElMessage.success('新增成功')
    } else {
      await updateDict(dialog.form.id, payload)
      ElMessage.success('保存成功')
    }
    dialog.visible = false
    fetchList()
  } catch (e) {
    // 拦截器已提示（编码重复会返回 1001「同类型下编码已存在」）
  } finally {
    dialog.submitting = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.name}」吗？如果已有业务数据引用该字典项，删除会失败。`,
      '删除字典项',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteDict(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 拦截器已提示
  }
}

/** 切换启用状态的快捷开关。复用编辑接口传全量字段 */
async function toggleEnabled(row) {
  const next = row.enabled === false
  try {
    await updateDict(row.id, {
      type: row.type,
      code: row.code,
      name: row.name,
      sort: row.sort ?? 0,
      enabled: next,
      remark: row.remark ?? null
    })
    row.enabled = next
    ElMessage.success(next ? '已启用' : '已停用')
  } catch (e) {
    // 失败时开关要弹回去，否则界面和数据不一致
    row.enabled = !next
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">数据字典</h2>
        <p class="page-desc">
          维护平台的下拉选项。字典类型固定为四类，它们会被商品、融资申请等业务模块直接引用。
          停用的字典项不会在前端下拉框出现。
        </p>
      </div>
      <div>
        <el-button :loading="loading" @click="fetchList">
          <el-icon><Refresh /></el-icon>
          <span style="margin-left: 4px">刷新</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span style="margin-left: 4px">新增字典项</span>
        </el-button>
      </div>
    </div>

    <!-- 类型用标签页切换，比下拉框更直观：一眼能看到有哪几类 -->
    <el-card shadow="never" class="type-card">
      <el-radio-group v-model="activeType" @change="handleTypeChange">
        <el-radio-button
          v-for="opt in DICT_TYPE_OPTIONS"
          :key="opt.value"
          :value="opt.value"
        >
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>

      <div class="type-hint">
        类型标识：<code>{{ activeType }}</code> —— 业务代码里用这个值引用本组字典
      </div>
    </el-card>

    <div class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />

        <el-table-column label="编码" width="160">
          <template #default="{ row }">
            <code class="code-chip">{{ row.code }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="显示名称" min-width="150" />

        <el-table-column prop="sort" label="排序" width="80" align="center" />

        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== false"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="toggleEnabled(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="165">
          <template #default="{ row }">
            <span class="mono">{{ row.updateTime || row.createTime || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty :description="`「${currentTypeName}」下暂无字典项`" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增字典项' : '编辑字典项'"
      width="540px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-width="90px">
        <el-form-item label="字典类型">
          <el-select v-model="dialog.form.type" style="width: 100%" disabled>
            <el-option
              v-for="opt in DICT_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <div class="field-hint">类型不可更改，如需迁移请删除后重建</div>
        </el-form-item>

        <el-form-item label="编码" prop="code">
          <el-input
            v-model.trim="dialog.form.code"
            placeholder="如 ORGANIC，仅字母数字下划线"
            maxlength="32"
          />
          <div class="field-hint">
            同类型下编码唯一。业务代码和接口参数都靠它取值，<b>建好后不要改</b>
          </div>
        </el-form-item>

        <el-form-item label="显示名称" prop="name">
          <el-input
            v-model.trim="dialog.form.name"
            placeholder="如 有机农产品，下拉框里显示的内容"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="dialog.form.sort" :min="0" :max="9999" />
          <span class="field-hint inline">数值越小越靠前</span>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
          <div class="field-hint">停用后不会出现在前端下拉框中</div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="dialog.form.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，说明这个选项的适用场景"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.type-card {
  margin-bottom: 16px;
}

.type-hint {
  margin-top: 14px;
  font-size: 12.5px;
  color: #8a9099;
}

.type-hint code {
  padding: 2px 6px;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  color: #16a34a;
  background: #f0fdf4;
  border-radius: 4px;
}

.code-chip {
  padding: 2px 7px;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #4b5563;
  background: #f3f4f6;
  border-radius: 4px;
}

.muted {
  color: #b0b5bd;
}

.mono {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12.5px;
  color: #4b5563;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #8a9099;
}

.field-hint.inline {
  margin: 0 0 0 10px;
}
</style>
