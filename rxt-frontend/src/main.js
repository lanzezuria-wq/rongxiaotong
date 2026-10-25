import { createApp } from 'vue'

import { ElLoading } from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 的组件默认是英文（分页器会显示 "Go to"、日期选择器是英文月份）。
// 引入中文语言包并注入，才能让内置文案变成中文。
import { ArrowDown, ArrowRight, ChatDotRound, CircleClose, Close, Collection, Connection, DocumentChecked, FolderOpened, Goods, InfoFilled, Iphone, List, Lock, Menu, Message, Picture, Plus, Refresh, Search, ShoppingCart, Stamp, Star, SwitchButton, TopRight, User, View, Warning } from '@element-plus/icons-vue'
import { ElAlert, ElAvatar, ElButton, ElCard, ElCheckbox, ElCol, ElConfigProvider, ElDescriptions, ElDescriptionsItem, ElDialog, ElDivider, ElDropdown, ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElIcon, ElImage, ElInput, ElInputNumber, ElLink, ElOption, ElPagination, ElProgress, ElRadio, ElRadioButton, ElRadioGroup, ElResult, ElRow, ElSelect, ElSkeleton, ElSwitch, ElTable, ElTableColumn, ElTag, ElTooltip } from 'element-plus'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

// 只注册实际使用的图标与组件，避免整库进入首屏。
for (const [name, component] of Object.entries({ ArrowDown, ArrowRight, ChatDotRound, CircleClose, Close, Collection, Connection, DocumentChecked, FolderOpened, Goods, InfoFilled, Iphone, List, Lock, Menu, Message, Picture, Plus, Refresh, Search, ShoppingCart, Stamp, Star, SwitchButton, TopRight, User, View, Warning })) {
  app.component(name, component)
}

for (const component of [ElAlert, ElAvatar, ElButton, ElCard, ElCheckbox, ElCol, ElConfigProvider, ElDescriptions, ElDescriptionsItem, ElDialog, ElDivider, ElDropdown, ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElIcon, ElImage, ElInput, ElInputNumber, ElLink, ElOption, ElPagination, ElProgress, ElRadio, ElRadioButton, ElRadioGroup, ElResult, ElRow, ElSelect, ElSkeleton, ElSwitch, ElTable, ElTableColumn, ElTag, ElTooltip]) app.component(component.name, component)
app.directive('loading', ElLoading.directive)
app.use(router)

// 全局兜底：捕获组件里没被 try/catch 接住的错误，避免整页白屏且控制台无提示
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局异常]', info, err)
}

app.mount('#app')
