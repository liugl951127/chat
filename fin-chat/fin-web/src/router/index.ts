import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginPage.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/chat/ChatPage.vue'),
        meta: { title: '聊天' }
      },
      {
        path: 'chat/:convId',
        name: 'chat-detail',
        component: () => import('@/views/chat/ChatPage.vue'),
        meta: { title: '聊天' }
      },
      {
        path: 'trade',
        name: 'trade',
        component: () => import('@/views/trade/TradePage.vue'),
        meta: { title: '交易' }
      },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/audit/AuditPage.vue'),
        meta: { title: '审计' }
      },
      {
        path: 'compliance',
        name: 'compliance',
        component: () => import('@/views/compliance/RiskTestPage.vue'),
        meta: { title: '风险测评' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && auth.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
