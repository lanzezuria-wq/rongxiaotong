<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listCart } from '@/api/cart'
import { listAddress, createAddress } from '@/api/address'
import { createOrder, payOrder } from '@/api/order'
import { formatPrice } from '@/utils/format'

/**
 * 确认订单页 —— 核对商品 + 选地址 + 提交订单 + Mock 支付。
 *
 * <p>入口由购物车「去结算」带 cartIds 跳过来。这里重新拉一遍购物车列表
 * 过滤出选中的项，拿到的是实时价格/库存，而不是相信 URL 里的 id。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const paying = ref(false)

const items = ref([])
const addresses = ref([])
const selectedAddressId = ref(null)

/** 下单成功后返回的订单（状态待付款），用于切换到支付面板 */
const createdOrder = ref(null)

const cartIds = computed(() => {
  const raw = route.query.cartIds
  if (!raw) return []
  return String(raw)
    .split(',')
    .map((n) => Number(n))
    .filter((n) => !Number.isNaN(n))
})

const totalPrice = computed(() => items.value.reduce((s, i) => s + Number(i.subtotal || 0), 0))

// ---- 新增地址弹窗 ----
const addrDialog = ref(false)
const addrSaving = ref(false)
const addrForm = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

async function load() {
  loading.value = true
  try {
    const all = (await listCart()) || []
    const idSet = new Set(cartIds.value)
    items.value = all.filter((i) => idSet.has(i.id) && i.productName != null)

    const addr = (await listAddress()) || []
    addresses.value = addr
    const def = addr.find((a) => a.isDefault === 1) || addr[0]
    selectedAddressId.value = def?.id ?? null
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  submitting.value = true
  try {
    createdOrder.value = await createOrder({
      cartIds: items.value.map((i) => i.id),
      addressId: selectedAddressId.value
    })
    ElMessage.success('订单创建成功')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

async function onPay() {
  if (!createdOrder.value) return
  paying.value = true
  try {
    await payOrder(createdOrder.value.id)
    ElMessage.success('支付成功')
    router.push('/buyer/orders')
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    paying.value = false
  }
}

function openAddrDialog() {
  Object.assign(addrForm, {
    receiver: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: 0
  })
  addrDialog.value = true
}

async function onSaveAddress() {
  if (!addrForm.receiver || !addrForm.phone || !addrForm.detail) {
    ElMessage.warning('请填写收货人、手机号和详细地址')
    return
  }
  addrSaving.value = true
  try {
    const id = await createAddress({ ...addrForm })
    ElMessage.success('地址已保存')
    addrDialog.value = false
    const addr = (await listAddress()) || []
    addresses.value = addr
    selectedAddressId.value = id ?? addr[0]?.id ?? null
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    addrSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="confirm-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">确认订单</h2>
          <p class="page-desc">核对商品与收货信息，提交后 15 分钟内需完成支付</p>
        </div>
      </div>

      <div v-loading="loading" class="confirm-body">
        <!-- 阶段一：未下单 -->
        <template v-if="!createdOrder">
          <el-empty
            v-if="!loading && items.length === 0"
            description="没有可结算的商品"
            style="padding: 60px 0"
          >
            <el-button type="primary" @click="$router.push('/cart')">返回购物车</el-button>
          </el-empty>

          <template v-else>
            <el-card shadow="never" class="block">
              <template #header><span class="card-title">商品清单</span></template>
              <div v-for="it in items" :key="it.id" class="item-row">
                <el-image :src="it.productImage" fit="cover" class="item-img">
                  <template #error><div class="item-img-fb"><el-icon><Picture /></el-icon></div></template>
                </el-image>
                <div class="item-info">
                  <div class="item-name">{{ it.productName }}</div>
                  <div v-if="it.skuSpec" class="item-sku">{{ it.skuSpec }}</div>
                </div>
                <div class="item-qty">×{{ it.quantity }}</div>
                <div class="item-sub">¥{{ formatPrice(it.subtotal) }}</div>
              </div>
              <div class="total-row">
                共 {{ items.length }} 种商品，合计：<b>¥{{ formatPrice(totalPrice) }}</b>
              </div>
            </el-card>

            <el-card shadow="never" class="block">
              <template #header>
                <div class="block-head">
                  <span class="card-title">收货地址</span>
                  <el-button text type="primary" @click="openAddrDialog">+ 新增地址</el-button>
                </div>
              </template>
              <el-empty v-if="addresses.length === 0" description="还没有收货地址，请先新增" />
              <el-radio-group v-else v-model="selectedAddressId" class="addr-list">
                <el-radio v-for="a in addresses" :key="a.id" :value="a.id" class="addr-item">
                  <span class="addr-text">{{ a.receiver }} {{ a.phone }} — {{ a.fullAddress }}</span>
                  <el-tag v-if="a.isDefault === 1" size="small" type="success" effect="plain">默认</el-tag>
                </el-radio>
              </el-radio-group>
            </el-card>

            <div class="submit-bar">
              <span class="total-row">
                应付：<b class="pay-amount">¥{{ formatPrice(totalPrice) }}</b>
              </span>
              <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">
                提交订单
              </el-button>
            </div>
          </template>
        </template>

        <!-- 阶段二：已下单，待支付 -->
        <template v-else>
          <el-card shadow="never" class="block">
            <el-result
              icon="success"
              title="订单创建成功"
              :sub-title="`订单号 ${createdOrder.orderNo}，请在 15 分钟内完成支付`"
            >
              <template #extra>
                <div class="pay-panel">
                  <div class="pay-amount">
                    应付金额：<b>¥{{ formatPrice(createdOrder.totalAmount) }}</b>
                  </div>
                  <el-button type="primary" size="large" :loading="paying" @click="onPay">
                    立即支付（Mock）
                  </el-button>
                  <el-button size="large" @click="$router.push('/buyer/orders')">稍后支付</el-button>
                </div>
              </template>
            </el-result>
          </el-card>
        </template>
      </div>
    </div>

    <el-dialog v-model="addrDialog" title="新增收货地址" width="480px">
      <el-form :model="addrForm" label-width="80px">
        <el-form-item label="收货人">
          <el-input v-model.trim="addrForm.receiver" maxlength="20" placeholder="收货人姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="addrForm.phone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="所在地区">
          <div class="region-row">
            <el-input v-model.trim="addrForm.province" placeholder="省" />
            <el-input v-model.trim="addrForm.city" placeholder="市" />
            <el-input v-model.trim="addrForm.district" placeholder="区/县" />
          </div>
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model.trim="addrForm.detail" maxlength="100" placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addrForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrDialog = false">取消</el-button>
        <el-button type="primary" :loading="addrSaving" @click="onSaveAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.confirm-page {
  padding-bottom: 40px;
}

.confirm-body {
  max-width: 900px;
}

.block {
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}

.item-row:last-of-type {
  border-bottom: none;
}

.item-img {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  flex-shrink: 0;
}

.item-img-fb {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
}

.item-info {
  flex: 1;
}

.item-name {
  font-size: 14px;
  color: #1f2329;
}

.item-sku {
  margin-top: 4px;
  font-size: 12px;
  color: #8a9099;
}

.item-qty {
  width: 60px;
  text-align: center;
  font-size: 13px;
  color: #6b7280;
}

.item-sub {
  width: 110px;
  text-align: right;
  font-size: 14px;
  color: #1f2329;
}

.total-row {
  padding-top: 12px;
  text-align: right;
  font-size: 14px;
  color: #6b7280;
}

.total-row b {
  color: #e64545;
  font-size: 18px;
}

.addr-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.addr-item {
  height: auto;
  padding: 6px 0;
  white-space: normal;
}

.addr-text {
  font-size: 14px;
  color: #1f2329;
}

.submit-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
}

.pay-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.pay-amount b {
  color: #e64545;
  font-size: 22px;
}

.region-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
