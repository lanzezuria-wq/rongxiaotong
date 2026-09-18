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
