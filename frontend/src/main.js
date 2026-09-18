import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// Element Plus 的样式必须先于自己的主题引入，否则我们的 :root 覆盖会被它盖回去
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/http'
import { useSessionStore } from './store/session'

// 放在最后：主题里的变量要压过 Element Plus 的默认值
import './styles/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// locale 换成中文，分页、表格空态提示等内置文案才会是中文
app.use(ElementPlus, { locale: zhCn })

/**
 * token 失效的兜底处理。
 *
 * http 层只负责「通知」，跳转到哪由应用决定 —— 这样 http 模块不必知道路由的存在，
 * 也避免了 http → router → store → api → http 的循环依赖。
 */
onUnauthorized((message) => {
  useSessionStore().clear()
  ElMessage.warning(message || '登录状态已失效，请重新登录')
  router.replace({ name: 'login' })
})

app.mount('#app')
