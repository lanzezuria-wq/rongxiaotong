<script setup>
import { ref, watch } from 'vue'
import { getOrder } from '@/api/order'
import { formatPrice, orderStatusLabel, orderStatusTagType } from '@/utils/format'

/**
 * 订单详情弹窗 —— 买家订单页 / 农户销售页共用的详情展示。
 *
 * <p>数据按需拉取：只有弹窗打开时才请求 /api/order/{id}，
 * 列表页不用为每一单都预载明细（后端也是这么设计的，列表轻、详情重）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const order = ref(null)

watch(
  () => props.modelValue,
  (v) => {
    if (v && props.orderId != null) load()
  }
)

async function load() {
  loading.value = true
  order.value = null
  try {
    order.value = await getOrder(props.orderId)
  } catch (e) {
    // 拦截器已统一提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="订单详情"
    width="640px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="od-wrap">
      <template v-if="order">
        <div class="od-head">
          <div>
            <div class="od-no">订单号：{{ order.orderNo }}</div>
            <div class="od-time">下单时间：{{ order.createdAt }}</div>
          </div>
          <el-tag :type="orderStatusTagType(order.status)">{{ orderStatusLabel(order.status) }}</el-tag>
        </div>

        <el-table :data="order.items || []" size="small">
          <el-table-column label="商品" min-width="180">
            <template #default="{ row }">
              <div>{{ row.productName }}</div>
              <div v-if="row.skuSpec" class="od-sku">{{ row.skuSpec }}</div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ formatPrice(row.subtotal) }}</template>
          </el-table-column>
        </el-table>

        <div class="od-rows">
          <div v-if="order.address" class="od-row">
            <span class="lbl">收货信息</span><span>{{ order.address }}</span>
          </div>
          <div v-if="order.expressCompany" class="od-row">
            <span class="lbl">快递</span><span>{{ order.expressCompany }} {{ order.expressNo }}</span>
          </div>
          <div v-if="order.shippedAt" class="od-row"><span class="lbl">发货时间</span><span>{{ order.shippedAt }}</span></div>
          <div v-if="order.receivedAt" class="od-row"><span class="lbl">收货时间</span><span>{{ order.receivedAt }}</span></div>
          <div class="od-row">
            <span class="lbl">实付金额</span><span class="od-total">¥{{ formatPrice(order.totalAmount) }}</span>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="加载失败" />
    </div>
  </el-dialog>
</template>

<style scoped>
.od-wrap {
  min-height: 160px;
}

.od-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.od-no {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.od-time {
  margin-top: 4px;
  font-size: 12px;
  color: #8a9099;
}

.od-sku {
  font-size: 12px;
  color: #8a9099;
}

.od-rows {
  margin-top: 16px;
}

.od-row {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  font-size: 13px;
  color: #4b5563;
}

.lbl {
  width: 68px;
  color: #8a9099;
  flex-shrink: 0;
}

.od-total {
  color: #e64545;
  font-weight: 600;
}
</style>
