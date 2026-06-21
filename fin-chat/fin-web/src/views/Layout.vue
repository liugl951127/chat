<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">FinChat</div>
      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>聊天</span>
        </el-menu-item>
        <el-menu-item index="/trade">
          <el-icon><Money /></el-icon>
          <span>交易</span>
        </el-menu-item>
        <el-menu-item index="/compliance">
          <el-icon><Document /></el-icon>
          <span>风险测评</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><DataLine /></el-icon>
          <span>审计查询</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="title">{{ $route.meta.title || 'FinChat' }}</div>
        <div class="user-info">
          <span class="user-name">{{ auth.nickname || '用户' }}</span>
          <span class="risk-tag" :class="`risk-c${auth.riskLevel}`">C{{ auth.riskLevel }}</span>
          <el-button text @click="onLogout">登出</el-button>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => {
  const p = router.currentRoute.value.path
  if (p.startsWith('/chat')) return '/chat'
  if (p.startsWith('/trade')) return '/trade'
  if (p.startsWith('/audit')) return '/audit'
  if (p.startsWith('/compliance')) return '/compliance'
  return '/chat'
})

const onLogout = async () => {
  try {
    await ElMessageBox.confirm('确认登出?', '提示', { type: 'warning' })
    await auth.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.sidebar { background: #001529; color: #fff; }
.logo {
  height: 56px; line-height: 56px; text-align: center;
  font-size: 18px; font-weight: 600; color: #fff;
  border-bottom: 1px solid #1f2d3d;
}
.sidebar :deep(.el-menu) { background: transparent; border: none; }
.sidebar :deep(.el-menu-item) { color: #abb3bd; }
.sidebar :deep(.el-menu-item.is-active) { color: #fff; background: #1890ff; }
.header {
  background: #fff; border-bottom: 1px solid #ebeef5;
  display: flex; align-items: center; justify-content: space-between;
}
.title { font-size: 16px; font-weight: 500; }
.user-info { display: flex; align-items: center; gap: 12px; }
.user-name { font-size: 14px; color: #606266; }
.risk-tag {
  padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600;
}
.risk-c1 { background: #f0f9eb; color: #67c23a; }
.risk-c2 { background: #ecf5ff; color: #409eff; }
.risk-c3 { background: #fdf6ec; color: #e6a23c; }
.risk-c4 { background: #fef0f0; color: #f56c6c; }
.risk-c5 { background: #f56c6c; color: #fff; }
</style>
