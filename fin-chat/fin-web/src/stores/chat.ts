import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<chatApi.Conversation[]>([])
  const currentConvId = ref<string>('')
  const messages = ref<Record<string, chatApi.ChatMessage[]>>({})

  const loadConversations = async () => {
    const resp = await chatApi.listConversations()
    conversations.value = resp.data || []
    return conversations.value
  }

  const createConversation = async (data: { advisorId?: number; title?: string }) => {
    const resp = await chatApi.createConversation(data)
    conversations.value.unshift(resp.data)
    return resp.data
  }

  const loadMessages = async (convId: string) => {
    const resp = await chatApi.listMessages(convId, 50)
    messages.value[convId] = (resp.data.records || []).reverse()
    currentConvId.value = convId
    return messages.value[convId]
  }

  const sendMessage = async (convId: string, content: string) => {
    const resp = await chatApi.sendMessage({ conversationId: convId, content, type: 'TEXT' })
    if (!messages.value[convId]) messages.value[convId] = []
    messages.value[convId].push(resp.data)
    return resp.data
  }

  const verifyChain = async (convId: string) => {
    const resp = await chatApi.verifyChain(convId)
    return resp.data
  }

  return {
    conversations, currentConvId, messages,
    loadConversations, createConversation, loadMessages, sendMessage, verifyChain
  }
})
