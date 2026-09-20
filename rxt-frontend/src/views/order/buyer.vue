<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOrders, payOrder, receiveOrder, cancelOrder } from '@/api/order'
import { orderStatusLabel, orderStatusTagType, ORDER_STATUS_FILTER, formatPrice } from '@/utils/format'
import OrderDetailDialog from '@/components/OrderDetailDialog.vue'

/**
 * 我的订单（买家视角）—— role=buyer 查询，按状态筛选。
 * 操作：待付款 → 支付/取消；已发货 → 确认收货。
 */
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detailOrderId = ref(null)

/** 倒计时：待付款订单显示剩余支付时间，用 now 驱动每秒刷新 */
const now = ref(Date.now())
let timer = null

async function load() {
  loading.value = true
  try {
    const data = await listOrders({
      role: 'buyer',
      status: query.status === '' ? undefined : query.status,
      page: query.page,
      size: query.size
    })
    records.value = data?.records || []
    total.value = data?.total || 0
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  query.page = 1
  load()
}

function onPageChange() {
  load()
}

function remainText(order) {
  if (!order.expireAt) return ''
  const ms = new Date(String(order.expireAt).replace(' ', 'T')).getTime() - now.value
  if (ms <= 0) return '已超时，即将自动取消'
  const s = Math.floor(ms / 1000)
  return `${Math.floor(s / 60)} 分 ${s % 60} 秒`
}

async function onPay(order) {
  try {
    await ElMessageBox.confirm(
      `确认支付 ¥${formatPrice(order.totalAmount)}？（演示环境为 Mock 支付）`,
      '支付',
      { confirmButtonText: '确认支付', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await payOrder(order.id)
    ElMessage.success('支付成功')
    load()
  } catch (e) {
    // 拦截器已统一提示
  }
}

async function onCancel(order) {
  try {
    await ElMessageBox.confirm('确定取消该订单吗？取消后库存会退回。', '提示', {
      type: 'warning',
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  try {
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    load()
  } catch (e) {
    // 拦截器已统一提示
  }
}

async function onReceive(order) {
  try {
    await ElMessageBox.confirm('确认已收到货？', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await receiveOrder(order.id)
    ElMessage.success('已确认收货')
    load()
  } catch (e) {
    // 拦截器已统一提示
  }
}

function openDetail(order) {
  detailOrderId.value = order.id
  detailVisible.value = true
}

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  load()
})

onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="order-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">我的订单</h2>
          <p class="page-desc">我购买的商品订单</p>
        </div>
      </div>

      <el-radio-group v-model="query.status" class="status-tabs" @change="onStatusChange">
        <el-radio-button v-for="s in ORDER_STATUS_FILTER" :key="s.label" :value="s.value">
          {{ s.label }}
        </el-radio-button>
      </el-radio-group>

      <div v-loading="loading" class="order-list">
        <el-card v-for="o in records" :key="o.id" shadow="never" class="order-card">
          <div class="oc-head">
            <div class="oc-meta">
              <span class="oc-no">订单号 {{ o.orderNo }}</span>
              <span class="oc-time">{{ o.createdAt }}</span>
            </div>
            <el-tag :type="orderStatusTagType(o.status)">{{ orderStatusLabel(o.status) }}</el-tag>
          </div>

          <div class="oc-body">
            <div class="oc-summary">
              <span>共 {{ o.itemCount }} 件商品</span>
              <span>卖家：{{ o.farmerName || '-' }}</span>
            </div>
            <div class="oc-amount">¥{{ formatPrice(o.totalAmount) }}</div>
          </div>

          <div v-if="o.status === 0 && o.expireAt" class="oc-countdown">
            支付剩余 <b>{{ remainText(o) }}</b>
          </div>

          <div class="oc-actions">
            <el-button size="small" @click="openDetail(o)">详情</el-button>
            <el-button v-if="o.status === 0" size="small" type="primary" @click="onPay(o)">
              立即支付
            </el-button>
            <el-button v-if="o.status === 0" size="small" type="danger" plain @click="onCancel(o)">
              取消订单
            </el-button>
            <el-button v-if="o.status === 2" size="small" type="success" @click="onReceive(o)">
              确认收货
            </el-button>
          </div>
        </el-card>

        <el-empty
          v-if="!loading && records.length === 0"
          description="暂无订单"
          style="padding: 60px 0"
        />

        <div v-if="total > query.size" class="pagination-bar">
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.size"
            :total="total"
            layout="prev, pager, next, total"
            background
            @current-change="onPageChange"
          />
        </div>
      </div>
    </div>

    <OrderDetailDialog v-model="detailVisible" :order-id="detailOrderId" />
  </div>
</template>

<style scoped>
.order-page {
  padding-bottom: 40px;
}

.status-tabs {
  margin-bottom: 16px;
}

.order-list {
  max-width: 900px;
}

.order-card {
  margin-bottom: 12px;
}

.oc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
}

.oc-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.oc-no {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}

.oc-time {
  font-size: 12px;
  color: #8a9099;
}

.oc-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.oc-summary {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #6b7280;
}

.oc-amount {
  font-size: 16px;
  font-weight: 700;
  color: #e64545;
}

.oc-countdown {
  font-size: 13px;
  color: #d97706;
}

.oc-countdown b {
  color: #dc2626;
}

.oc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}
</style>
