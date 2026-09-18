import { createApp } from 'vue'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 的组件默认是英文（分页器会显示 "Go to"、日期选择器是英文月份）。
// 引入中文语言包并注入，才能让内置文案变成中文。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

/**
 * 注册 Element Plus 的全部图标组件。
 *
 * 图标是**组件**而不是字体，所以必须显式注册才能在模板里写 <el-icon><Search /></el-icon>。
 * 逐个注册 200 多个太啰嗦，这里用循环批量注册。
 * 生产环境如果在意包体积，可以改成按需引入。
 */
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 全局兜底：捕获组件里没被 try/catch 接住的错误，避免整页白屏且控制台无提示
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局异常]', info, err)
}

app.mount('#app')
