<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCart, updateCartQuantity, deleteCart } from '@/api/cart'
import { formatPrice } from '@/utils/format'

/**
 * 购物车页 —— 勾选 + 改数量 + 删除 + 结算。
 *
 * <p>商品被删除/下架时后端返回的商品信息为 null（CartVO.from 只在商品存在时填字段），
 * 这类项标为「已失效」：不可勾选、不可结算，只能移除。
 */
const router = useRouter()

const loading = ref(false)
const items = ref([])
const selected = ref(new Set())

const validItems = computed(() => items.value.filter((i) => i.productName != null))
const selectedItems = computed(() => validItems.value.filter((i) => selected.value.has(i.id)))

const allChecked = computed(
  () => validItems.value.length > 0 && selectedItems.value.length === validItems.value.length
)

const totalPrice = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + Number(i.subtotal || 0), 0)
)

async function load() {
  loading.value = true
  try {
    items.value = (await listCart()) || []
    // 清理已不存在的选中项（比如刚删除的那行）
    const ids = new Set(items.value.map((i) => i.id))
    selected.value = new Set([...selected.value].filter((id) => ids.has(id)))
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function toggleAll(val) {
  selected.value = val ? new Set(validItems.value.map((i) => i.id)) : new Set()
}

function toggleItem(id) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

async function onQuantityChange(item, qty) {
  if (qty == null) return
  try {
    await updateCartQuantity(item.id, qty)
    ElMessage.success('已更新')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    load()
  }
}

async function onRemove(item) {
  try {
    await ElMessageBox.confirm(`确定从购物车移除「${item.productName || '该商品'}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteCart(item.id)
    ElMessage.success('已移除')
    load()
  } catch (e) {
    // 拦截器已统一提示
  }
}

function goCheckout() {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  const ids = selectedItems.value.map((i) => i.id).join(',')
  router.push(`/order/confirm?cartIds=${ids}`)
}

onMounted(load)
</script>

<template>
  <div class="cart-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">购物车</h2>
          <p class="page-desc">勾选要结算的商品，可修改数量或移除</p>
        </div>
      </div>

      <el-card v-loading="loading" shadow="never" :body-style="{ padding: '0 16px' }">
        <template v-if="items.length">
          <div class="cart-head">
            <el-checkbox :model-value="allChecked" @update:model-value="toggleAll">全选</el-checkbox>
            <span />
            <span class="head-label">商品</span>
            <span class="head-label">单价</span>
            <span class="head-label">数量</span>
            <span class="head-label">小计</span>
            <span class="head-label">操作</span>
          </div>

          <div
            v-for="item in items"
            :key="item.id"
            class="cart-row"
            :class="{ invalid: item.productName == null }"
          >
            <el-checkbox
              :model-value="selected.has(item.id)"
              :disabled="item.productName == null"
              @update:model-value="() => toggleItem(item.id)"
            />
            <el-image :src="item.productImage" fit="cover" class="row-img">
              <template #error><div class="row-img-fb"><el-icon><Picture /></el-icon></div></template>
            </el-image>
            <div class="row-name">
              <div
                v-if="item.productName"
                class="row-title"
                @click="$router.push(`/products/${item.productId}`)"
              >
                {{ item.productName }}
              </div>
              <div v-else class="row-invalid">商品已失效，请移除</div>
              <div v-if="item.skuSpec" class="row-sku">{{ item.skuSpec }}</div>
            </div>
            <div class="cell">¥{{ formatPrice(item.price) }}</div>
            <div class="cell">
              <el-input-number
                v-if="item.productName != null"
                :model-value="item.quantity"
                :min="1"
                :max="Math.min(999, item.stock || 1)"
                size="small"
                @change="(v) => onQuantityChange(item, v)"
              />
            </div>
            <div class="cell cell-sub">¥{{ formatPrice(item.subtotal) }}</div>
            <div class="cell">
              <el-button text type="danger" @click="onRemove(item)">移除</el-button>
            </div>
          </div>
        </template>

        <el-empty v-else-if="!loading" description="购物车还是空的" style="padding: 40px 0">
          <el-button type="primary" @click="$router.push('/products')">去逛逛</el-button>
        </el-empty>
      </el-card>

      <div v-if="items.length" class="checkout-bar">
        <div class="cb-left">
          已选 <b class="cb-count">{{ selectedItems.length }}</b> 件
          <span class="cb-total">
            合计：<b>¥{{ formatPrice(totalPrice) }}</b>
          </span>
        </div>
        <el-button type="primary" size="large" @click="goCheckout">去结算</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  padding-bottom: 40px;
}

.cart-head,
.cart-row {
  display: grid;
  grid-template-columns: 90px 72px 1fr 110px 150px 110px 80px;
  gap: 12px;
  align-items: center;
}

.cart-head {
  padding: 12px 0;
  font-size: 13px;
  color: #8a9099;
  border-bottom: 1px solid #f0f2f5;
}

.head-label {
  text-align: center;
}

.cart-head .head-label:first-of-type {
  text-align: left;
}

.cart-row {
  padding: 16px 0;
  border-bottom: 1px solid #f0f2f5;
}

.cart-row:last-child {
  border-bottom: none;
}

.cart-row.invalid {
  opacity: 0.6;
}

.row-img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
}

.row-img-fb {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
}

.row-title {
  font-size: 14px;
  color: #1f2329;
  cursor: pointer;
}

.row-title:hover {
  color: #16a34a;
}

.row-invalid {
  font-size: 13px;
  color: #dc2626;
}

.row-sku {
  margin-top: 4px;
  font-size: 12px;
  color: #8a9099;
}

.cell {
  text-align: center;
  font-size: 14px;
  color: #1f2329;
}

.cell-sub {
  color: #e64545;
  font-weight: 600;
}

.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  margin-top: 16px;
  background: #fff;
  border-radius: 8px;
}

.cb-left {
  font-size: 14px;
  color: #6b7280;
}

.cb-count {
  color: #e64545;
}

.cb-total {
  margin-left: 20px;
}

.cb-total b {
  color: #e64545;
  font-size: 20px;
}
</style>
