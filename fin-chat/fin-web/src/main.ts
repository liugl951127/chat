import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

// 全局注册 Element Plus 图标
for (const [key, comp] of Object.entries(ElementPlusIcons)) {
  app.component(key, comp)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
