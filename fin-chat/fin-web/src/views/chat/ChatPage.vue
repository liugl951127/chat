<template>
  <div class="chat-layout">
    <!-- 左侧: 会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <h3>会话</h3>
        <el-button size="small" type="primary" @click="onNewConv">+ 新建</el-button>
      </div>
      <div class="conv-list">
        <div v-for="c in chat.conversations" :key="c.id"
             :class="['conv-item', { active: c.id === chat.currentConvId }]"
             @click="onSelectConv(c.id)">
          <div class="conv-title">{{ c.title }}</div>
          <div class="conv-meta">
            <span>{{ c.msgCount }} 条消息</span>
            <span v-if="c.lastMsgAt" class="time">{{ formatTime(c.lastMsgAt) }}</span>
          </div>
        </div>
        <div v-if="!chat.conversations.length" class="empty">
          暂无会话, 点击右上角新建
        </div>
      </div>
    </div>

    <!-- 右侧: 聊天窗口 -->
    <div class="chat-main">
      <div class="chat-header">
        <span v-if="currentConv">{{ currentConv.title }}</span>
        <span v-else>请选择会话</span>
        <el-button v-if="currentConv" size="small" text @click="onVerifyChain">
          <el-icon><Lock /></el-icon> 验证存证
        </el-button>
      </div>

      <div ref="messageList" class="chat-messages">
        <div v-for="m in currentMessages" :key="m.msgId"
             :class="['message-item', isMine(m) ? 'mine' : '']">
          <el-avatar :size="36" :icon="UserFilled" />
          <div>
            <div class="message-bubble">{{ m.content }}</div>
            <div class="message-time">
              {{ formatTime(m.serverTs) }}
              <el-tooltip v-if="m.contentHash" content="已上链存证" placement="top">
                <el-icon style="margin-left:4px; color:#67c23a;"><CircleCheck /></el-icon>
              </el-tooltip>
            </div>
          </div>
        </div>
        <div v-if="!currentMessages.length && currentConv" class="empty">
          暂无消息, 开始聊天吧
        </div>
      </div>

      <div class="chat-composer">
        <el-input v-model="draft" type="textarea" :rows="2" resize="none"
                  placeholder="请输入消息... (Ctrl+Enter 发送)"
                  @keydown.ctrl.enter="onSend" :disabled="!currentConv" />
        <el-button type="primary" @click="onSend" :loading="sending" :disabled="!currentConv">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useWebSocket } from '@/composables/useWebSocket'
import { useRoute } from 'vue-router'

const route = useRoute()
const chat = useChatStore()
const auth = useAuthStore()

const draft = ref('')
const sending = ref(false)
const messageList = ref<HTMLElement>()

const currentConv = computed(() => {
  const id = chat.currentConvId || (route.params.convId as string)
  return chat.conversations.find(c => c.id === id)
})

const currentMessages = computed(() => {
  const id = currentConv.value?.id
  return id ? (chat.messages[id] || []) : []
})

const isMine = (m: any) => m.senderId === auth.userId

const formatTime = (ts: any) => {
  if (!ts) return ''
  const d = typeof ts === 'number' ? new Date(ts) : new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

const onNewConv = async () => {
  try {
    const c = await chat.createConversation({ title: `会话 ${new Date().toLocaleString('zh-CN')}` })
    onSelectConv(c.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  }
}

const onSelectConv = async (id: string) => {
  await chat.loadMessages(id)
  scrollToBottom()
}

const onSend = async () => {
  if (!draft.value.trim() || !currentConv.value) return
  sending.value = true
  try {
    await chat.sendMessage(currentConv.value.id, draft.value)
    draft.value = ''
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const onVerifyChain = async () => {
  if (!currentConv.value) return
  try {
    const r = await chat.verifyChain(currentConv.value.id)
    ElMessage.success(`哈希链验证: ${r.status || 'OK'}, lastHash=${(r.lastHash || '').substring(0, 16)}...`)
  } catch (e: any) {
    ElMessage.error(e?.message || '验证失败')
  }
}

// WebSocket
const { connect: wsConnect } = useWebSocket((msg) => {
  if (msg.conversationId && chat.messages[msg.conversationId]) {
    chat.messages[msg.conversationId].push(msg)
    scrollToBottom()
  }
})

onMounted(async () => {
  await chat.loadConversations()
  if (auth.accessToken) {
    wsConnect(auth.accessToken)
  }
  // 如果有路由参数, 自动选
  if (route.params.convId) {
    onSelectConv(route.params.convId as string)
  }
})
</script>

<style scoped>
.sidebar-header {
  padding: 12px 16px; border-bottom: 1px solid #ebeef5;
  display: flex; align-items: center; justify-content: space-between;
}
.sidebar-header h3 { font-size: 14px; }
.conv-list { padding: 8px 0; }
.conv-item {
  padding: 10px 16px; cursor: pointer; border-bottom: 1px solid #f0f0f0;
}
.conv-item:hover { background: #f5f7fa; }
.conv-item.active { background: #ecf5ff; }
.conv-title { font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.conv-meta {
  display: flex; justify-content: space-between; font-size: 12px; color: #909399;
}
.empty { padding: 24px; text-align: center; color: #909399; font-size: 13px; }
</style>
