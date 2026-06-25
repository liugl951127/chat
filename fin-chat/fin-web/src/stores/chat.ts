import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as chatApi from '@/api/chat'

/**
 * chat store
 *
 * axios 拦截器保持 resp 全响应, 所以 store 拿到的 resp 是 ApiResponse,
 * 业务数据在 resp.data 里
 */
export const useChatStore = defineStore('chat', () => {
  const conversations = ref<chatApi.Conversation[]>([])
  const currentConvId = ref<string>('')
  const messages = ref<Record<string, chatApi.ChatMessage[]>>({})

  /** 兼容后端可能返回多种格式 */
  const normalizeList = (resp: any): any[] => {
    if (Array.isArray(resp)) return resp
    if (resp && Array.isArray(resp.records)) return resp.records
    if (resp && Array.isArray(resp.data)) return resp.data
    return []
  }

  const loadConversations = async () => {
    const resp = await chatApi.listConversations()
    const list = normalizeList(resp?.data)
    conversations.value = list.filter(c => c && c.id)   // 过滤 null/undefined
    return conversations.value
  }

  const createConversation = async (data: { advisorId?: number; title?: string }) => {
    const resp = await chatApi.createConversation(data)
    const conv = resp?.data
    if (conv && conv.id) {
      conversations.value.unshift(conv)
    }
    return conv
  }

  const loadMessages = async (convId: string) => {
    const resp = await chatApi.listMessages(convId, 50)
    const list = normalizeList(resp?.data).reverse()
    messages.value[convId] = list
    currentConvId.value = convId
    return list
  }

  const sendMessage = async (convId: string, content: string) => {
    const resp = await chatApi.sendMessage({
      conversationId: convId, content, type: 'TEXT'
    })
    const msg = resp?.data
    if (msg && msg.msgId) {
      if (!messages.value[convId]) messages.value[convId] = []
      messages.value[convId].push(msg)
    }
    return msg
  }

  const verifyChain = async (convId: string) => {
    const resp = await chatApi.verifyChain(convId)
    return resp?.data
  }

  return {
    conversations, currentConvId, messages,
    loadConversations, createConversation, loadMessages, sendMessage, verifyChain
  }
})