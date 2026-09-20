<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listOrders, shipOrder } from '@/api/order'
import { orderStatusLabel, orderStatusTagType, ORDER_STATUS_FILTER, formatPrice } from '@/utils/format'
import OrderDetailDialog from '@/components/OrderDetailDialog.vue'

/**
 * 我的销售（农户视角）—— role=farmer 查询我卖出的订单。
 * 操作：待发货 → 发货（填快递公司和单号）。
 */
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detailOrderId = ref(null)

const shipDialog = ref(false)
const shipping = ref(false)
const shipForm = reactive({ orderId: null, expressCompany: '', expressNo: '' })

async function load() {
  loading.value = true
  try {
    const data = await listOrders({
      role: 'farmer',
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

function openDetail(order) {
  detailOrderId.value = order.id
  detailVisible.value = true
}

function openShip(order) {
  shipForm.orderId = order.id
  shipForm.expressCompany = ''
  shipForm.expressNo = ''
  shipDialog.value = true
}

async function onShip() {
  if (!shipForm.expressCompany || !shipForm.expressNo) {
    ElMessage.warning('请填写快递公司和单号')
    return
  }
  shipping.value = true
  try {
    await shipOrder(shipForm.orderId, {
      expressCompany: shipForm.expressCompany,
      expressNo: shipForm.expressNo
    })
    ElMessage.success('发货成功')
    shipDialog.value = false
    load()
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    shipping.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="order-page">
    <div class="container">
      <div class="page-header" style="padding-top: 20px">
        <div>
          <h2 class="page-title">我的销售</h2>
          <p class="page-desc">我售出的商品订单，待发货的订单在这里发货</p>
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
              <span>买家：{{ o.buyerName || '-' }}</span>
            </div>
            <div class="oc-amount">¥{{ formatPrice(o.totalAmount) }}</div>
          </div>

          <div class="oc-actions">
            <el-button size="small" @click="openDetail(o)">详情</el-button>
            <el-button v-if="o.status === 1" size="small" type="primary" @click="openShip(o)">
              发货
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

    <el-dialog v-model="shipDialog" title="订单发货" width="440px">
      <el-form :model="shipForm" label-width="80px">
        <el-form-item label="快递公司">
          <el-input v-model.trim="shipForm.expressCompany" maxlength="50" placeholder="如：顺丰速运" />
        </el-form-item>
        <el-form-item label="快递单号">
          <el-input v-model.trim="shipForm.expressNo" maxlength="50" placeholder="请输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" :loading="shipping" @click="onShip">确认发货</el-button>
      </template>
    </el-dialog>
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

.oc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}
</style>
