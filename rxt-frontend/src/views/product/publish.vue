<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { publishProduct } from '@/api/product'
import { PRODUCT_CATEGORY_OPTIONS } from '@/utils/format'

/**
 * 发布商品（仅农户）—— 表单 + 图片 URL + SKU 规格配置。
 *
 * <p>关于图片：后端目前没有独立的上传接口（P2 任务清单里标了"可延后"），
 * 所以这里用「粘贴图片 URL」的方式收集，配合一个"示例图"按钮方便演示。
 * 等 P2 补了 /api/upload/image 后，再把这里的输入替换成 el-upload 即可。
 */
const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const imageInput = ref('')

const form = reactive({
  name: '',
  categoryCode: '',
  price: null,
  stock: null,
  unit: '',
  description: '',
  images: [],
  skuList: []
})

const rules = {
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 50, message: '商品名称不超过 50 字', trigger: 'blur' }
  ],
  categoryCode: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入计量单位', trigger: 'blur' }]
}

function addImage() {
  const url = imageInput.value.trim()
  if (!url) return
  if (form.images.length >= 6) {
    ElMessage.warning('最多 6 张图')
    return
  }
  form.images.push(url)
  imageInput.value = ''
}

function addSampleImage() {
  if (form.images.length >= 6) {
    ElMessage.warning('最多 6 张图')
    return
  }
  form.images.push(`https://picsum.photos/seed/rxt${Date.now()}/600/400`)
}

function removeImage(i) {
  form.images.splice(i, 1)
}

function addSku() {
  form.skuList.push({ spec: '', price: null, stock: null })
}

function removeSku(i) {
  form.skuList.splice(i, 1)
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.images.length < 1) {
    ElMessage.warning('请至少上传 1 张商品图')
    return
  }
  for (const s of form.skuList) {
    if (!s.spec || s.price == null || s.stock == null) {
      ElMessage.warning('规格行的规格名、单价、库存都要填写完整')
      return
    }
  }

  submitting.value = true
  try {
    await publishProduct({
      name: form.name,
      categoryCode: form.categoryCode,
      price: form.price,
      stock: form.stock,
      unit: form.unit,
      description: form.description || undefined,
      images: form.images,
      skuList: form.skuList.length ? form.skuList : undefined
    })
    ElMessage.success('发布成功')
    router.push('/farmer/sales')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="publish-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">发布商品</h2>
          <p class="page-desc">填写商品信息，发布后默认上架</p>
        </div>
      </div>

      <el-card shadow="never" class="publish-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model.trim="form.name" maxlength="50" placeholder="如：五常稻花香大米 25kg" />
          </el-form-item>

          <el-form-item label="商品分类" prop="categoryCode">
            <el-select v-model="form.categoryCode" placeholder="请选择分类" style="width: 240px">
              <el-option
                v-for="c in PRODUCT_CATEGORY_OPTIONS"
                :key="c.value"
                :value="c.value"
                :label="c.label"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="单价" prop="price">
            <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" />
            <span class="hint">元</span>
          </el-form-item>

          <el-form-item label="库存" prop="stock">
            <el-input-number v-model="form.stock" :min="0" :step="1" />
            <span class="hint">件</span>
          </el-form-item>

          <el-form-item label="计量单位" prop="unit">
            <el-input v-model.trim="form.unit" maxlength="10" placeholder="如：袋 / 箱 / 斤" style="width: 200px" />
          </el-form-item>

          <el-form-item label="商品图片">
            <div class="img-editor">
              <div v-for="(img, i) in form.images" :key="i" class="img-thumb">
                <el-image :src="img" fit="cover" class="img-preview">
                  <template #error><div class="img-fb"><el-icon><Picture /></el-icon></div></template>
                </el-image>
                <el-icon class="img-remove" @click="removeImage(i)"><CircleClose /></el-icon>
              </div>

              <div v-if="form.images.length < 6" class="img-add">
                <el-input v-model.trim="imageInput" placeholder="粘贴图片 URL" @keyup.enter="addImage" />
                <el-button @click="addImage">添加</el-button>
                <el-button text type="primary" @click="addSampleImage">示例图</el-button>
              </div>
            </div>
            <div class="hint">1~6 张，暂时用图片 URL（后端上传接口后续接入）</div>
          </el-form-item>

          <el-form-item label="规格 SKU">
            <div class="sku-editor">
              <div v-if="form.skuList.length" class="sku-table">
                <div class="sku-row sku-head">
                  <span>规格名</span>
                  <span>规格价（元）</span>
                  <span>规格库存</span>
                  <span />
                </div>
                <div v-for="(s, i) in form.skuList" :key="i" class="sku-row">
                  <el-input v-model.trim="s.spec" maxlength="20" placeholder="如：10kg/袋" />
                  <el-input-number v-model="s.price" :min="0.01" :precision="2" :step="1" size="small" />
                  <el-input-number v-model="s.stock" :min="0" :step="1" size="small" />
                  <el-button text type="danger" @click="removeSku(i)">删除</el-button>
                </div>
              </div>
              <el-button text type="primary" @click="addSku">+ 添加规格</el-button>
              <div class="hint">无规格商品可不填，买家按基础单价购买</div>
            </div>
          </el-form-item>

          <el-form-item label="商品描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="介绍产地、品质、规格等信息"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">
              发布商品
            </el-button>
            <el-button size="large" @click="router.back()">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.publish-page {
  padding-bottom: 40px;
}

.publish-card {
  max-width: 760px;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: #8a9099;
}

.img-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  width: 100%;
}

.img-thumb {
  position: relative;
  width: 96px;
  height: 96px;
}

.img-preview,
.img-fb {
  width: 96px;
  height: 96px;
  border-radius: 6px;
}

.img-fb {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
}

.img-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  font-size: 18px;
  color: #dc2626;
  cursor: pointer;
  background: #fff;
  border-radius: 50%;
}

.img-add {
  display: flex;
  align-items: center;
  gap: 8px;
}

.img-add .el-input {
  width: 260px;
}

.sku-editor {
  width: 100%;
}

.sku-table {
  margin-bottom: 8px;
}

.sku-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr 60px;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.sku-head {
  font-size: 13px;
  color: #8a9099;
}
</style>
