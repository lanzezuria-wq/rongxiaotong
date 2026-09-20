/**
 * 枚举值 → 中文展示 / 标签颜色的映射表。
 *
 * <p>为什么不直接在模板里写 {@code v-if} 判断？因为同一个枚举会在很多地方出现
 * （用户列表、专家审核、个人中心、顶部头像……），散落着写必然出现
 * "这里显示'正常'、那里显示'已启用'"这种不一致。集中到一张表里，改一次全站生效。
 *
 * <p>取值来源是后端 com.rxt.common.constant 下的四个常量类，改后端常量时这里要同步。
 */

// ==================== 用户角色 ====================

export const ROLE_OPTIONS = [
  { value: 'farmer', label: '农户 / 买家' },
  { value: 'expert', label: '农业专家' },
  { value: 'bank', label: '银行专员' },
  { value: 'admin', label: '平台管理员' }
]

const ROLE_LABEL = {
  farmer: '农户 / 买家',
  expert: '农业专家',
  bank: '银行专员',
  admin: '平台管理员'
}

/** Element Plus 的 el-tag type：可选取值是固定几个，不是任意颜色 */
const ROLE_TAG = {
  farmer: 'primary',
  expert: 'success',
  bank: 'warning',
  admin: 'danger'
}

export function roleLabel(role) {
  return ROLE_LABEL[role] || role || '-'
}

export function roleTagType(role) {
  return ROLE_TAG[role] || 'info'
}

// ==================== 用户状态 ====================
// 后端 UserConst：0 待审核 / 1 正常 / 2 已拒绝 / 3 已禁用

const USER_STATUS = {
  0: { label: '待审核', tag: 'warning', cls: 'status-pending' },
  1: { label: '正常', tag: 'success', cls: 'status-normal' },
  2: { label: '已拒绝', tag: 'danger', cls: 'status-rejected' },
  3: { label: '已禁用', tag: 'info', cls: 'status-disabled' }
}

export function userStatusLabel(status) {
  return USER_STATUS[status]?.label ?? '未知'
}

export function userStatusTagType(status) {
  return USER_STATUS[status]?.tag ?? 'info'
}

export function userStatusClass(status) {
  return USER_STATUS[status]?.cls ?? ''
}

/** 用户管理页的筛选下拉框：只允许在"正常 / 已禁用"之间切换（后端 UserStatusDTO 有校验） */
export const USER_STATUS_FILTER_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '待审核' },
  { value: 2, label: '已拒绝' },
  { value: 3, label: '已禁用' }
]

// ==================== 文章状态 ====================
// 后端 ArticleConst：0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架

const ARTICLE_STATUS = {
  0: { label: '待审核', tag: 'warning' },
  1: { label: '已发布', tag: 'success' },
  2: { label: '已拒绝', tag: 'danger' },
  3: { label: '已下架', tag: 'info' }
}

export function articleStatusLabel(status) {
  return ARTICLE_STATUS[status]?.label ?? '未知'
}

export function articleStatusTagType(status) {
  return ARTICLE_STATUS[status]?.tag ?? 'info'
}

export const ARTICLE_STATUS_OPTIONS = [
  { value: 0, label: '待审核' },
  { value: 1, label: '已发布' },
  { value: 2, label: '已拒绝' },
  { value: 3, label: '已下架' }
]

// ==================== 审核动作 ====================
// 后端 AuditConst：APPROVED / REJECTED

export const AUDIT_APPROVED = 'APPROVED'
export const AUDIT_REJECTED = 'REJECTED'

// ==================== 数据字典类型 ====================
// 后端 DictConst.ORDERED_TYPES，顺序有业务含义，不要改

export const DICT_TYPE_OPTIONS = [
  { value: 'product_category', label: '产品分类' },
  { value: 'fund_usage', label: '资金用途' },
  { value: 'industry', label: '所属行业' },
  { value: 'loan_term', label: '贷款期限' }
]

export function dictTypeLabel(type) {
  return DICT_TYPE_OPTIONS.find((o) => o.value === type)?.label || type || '-'
}

// ==================== 商品分类 ====================
// 后端 DictConst.ORDERED_TYPES 里的 product_category，6 个分类取值来自 init.sql。
// 商品列表的分类筛选对游客也开放，而字典接口 /admin/dict/** 要求登录，
// 所以这里写死一份兜底，游客不用登录也能筛。

export const PRODUCT_CATEGORY_OPTIONS = [
  { value: 'GRAIN', label: '粮食作物' },
  { value: 'VEG', label: '蔬菜' },
  { value: 'FRUIT', label: '水果' },
  { value: 'LIVESTOCK', label: '畜禽' },
  { value: 'AQUATIC', label: '水产' },
  { value: 'CASH', label: '经济作物' }
]

export function productCategoryLabel(code) {
  return PRODUCT_CATEGORY_OPTIONS.find((o) => o.value === code)?.label || code || '-'
}

// ==================== 金额 ====================
// 后端金额是 BigDecimal，Jackson 序列化成 JSON 数字后到 JS 变成 number，
// 会丢掉末尾的 0（168.00 → 168）。展示统一用 toFixed(2) 补齐两位小数。

export function formatPrice(value) {
  const n = Number(value)
  return Number.isNaN(n) ? '-' : n.toFixed(2)
}

// ==================== 订单状态 ====================
// 后端 OrderConst：0 待付款 / 1 待发货 / 2 已发货 / 3 已完成 / 4 已取消

const ORDER_STATUS = {
  0: { label: '待付款', tag: 'warning' },
  1: { label: '待发货', tag: 'primary' },
  2: { label: '已发货', tag: 'info' },
  3: { label: '已完成', tag: 'success' },
  4: { label: '已取消', tag: 'info' }
}

export function orderStatusLabel(status) {
  return ORDER_STATUS[status]?.label ?? '未知'
}

export function orderStatusTagType(status) {
  return ORDER_STATUS[status]?.tag ?? 'info'
}

/** 订单页的状态筛选 tabs。'' 表示「全部」 */
export const ORDER_STATUS_FILTER = [
  { value: '', label: '全部' },
  { value: 0, label: '待付款' },
  { value: 1, label: '待发货' },
  { value: 2, label: '已发货' },
  { value: 3, label: '已完成' },
  { value: 4, label: '已取消' }
]

// ==================== 融资申请状态 ====================
// 后端 FinancingConst：0 待匹配 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝 / 5 已取消
// 状态中文名后端已在 statusName 里给全，这里只补 el-tag 的颜色

const FINANCING_STATUS = {
  0: { label: '待匹配', tag: 'info' },
  1: { label: '已匹配', tag: 'primary' },
  2: { label: '待银行审批', tag: 'warning' },
  3: { label: '已通过', tag: 'success' },
  4: { label: '已拒绝', tag: 'danger' },
  5: { label: '已取消', tag: 'info' }
}

export function financingStatusLabel(status) {
  return FINANCING_STATUS[status]?.label ?? '未知'
}

export function financingStatusTagType(status) {
  return FINANCING_STATUS[status]?.tag ?? 'info'
}

// ==================== 联保小组状态 ====================
// 后端 FinancingConst：0 招募中 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝

const GROUP_STATUS = {
  0: { label: '招募中', tag: 'info' },
  1: { label: '已匹配', tag: 'primary' },
  2: { label: '待银行审批', tag: 'warning' },
  3: { label: '已通过', tag: 'success' },
  4: { label: '已拒绝', tag: 'danger' }
}

export function groupStatusLabel(status) {
  return GROUP_STATUS[status]?.label ?? '未知'
}

export function groupStatusTagType(status) {
  return GROUP_STATUS[status]?.tag ?? 'info'
}

// ==================== 提问状态 ====================
// 后端 Question：0 待回答 / 1 已回答

export function questionStatusLabel(status) {
  return status === 1 ? '已回答' : status === 0 ? '待回答' : '未知'
}

export function questionStatusTagType(status) {
  return status === 1 ? 'success' : 'warning'
}

// ==================== 融资字典选项 ====================
// 与 init.sql 的 t_dict 种子一致（资金用途 / 所属行业 / 贷款期限）。
// 融资表单下拉框用；值即后端校验的字典编码。

export const FUND_USAGE_OPTIONS = [
  { value: 'SEED', label: '种子化肥' },
  { value: 'MACHINE', label: '农机具购置' },
  { value: 'GREENHOUSE', label: '大棚建设' },
  { value: 'LAND', label: '土地流转' },
  { value: 'BREED', label: '畜禽养殖' },
  { value: 'PROCESS', label: '加工流通' }
]

export const INDUSTRY_OPTIONS = [
  { value: 'PLANTING', label: '种植业' },
  { value: 'BREEDING', label: '养殖业' },
  { value: 'PROCESSING', label: '农产品加工' },
  { value: 'SERVICE', label: '农业服务' },
  { value: 'ECOMMERCE', label: '农村电商' }
]

export const LOAN_TERM_OPTIONS = [
  { value: 'M3', label: '3 个月' },
  { value: 'M6', label: '6 个月' },
  { value: 'M12', label: '12 个月' },
  { value: 'M24', label: '24 个月' },
  { value: 'M36', label: '36 个月' }
]

function labelOf(options, code) {
  return options.find((o) => o.value === code)?.label || code || '-'
}

export function fundUsageLabel(code) {
  return labelOf(FUND_USAGE_OPTIONS, code)
}

export function industryLabel(code) {
  return labelOf(INDUSTRY_OPTIONS, code)
}

export function loanTermLabel(code) {
  return labelOf(LOAN_TERM_OPTIONS, code)
}
