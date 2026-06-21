import request from './request'

export interface Conversation {
  id: string
  type: string
  title: string
  userId: number
  advisorId?: number
  status: number
  lastMsgId?: string
  lastMsgAt?: string
  msgCount: number
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  msgId: string
  conversationId: string
  senderId: number
  senderType: string
  type: string
  content: string
  contentHash: string
  prevHash?: string
  serverTs: number
}

/** 会话列表 */
export const listConversations = () =>
  request.get<Conversation[]>('/api/v1/chat/conversations')

/** 创建会话 */
export const createConversation = (data: { advisorId?: number; type?: string; title?: string }) =>
  request.post<Conversation>('/api/v1/chat/conversations', data)

/** 发送消息 */
export const sendMessage = (data: { conversationId: string; content: string; type?: string }) =>
  request.post<ChatMessage>('/api/v1/chat/messages', data)

/** 消息历史 */
export const listMessages = (conversationId: string, size = 20) =>
  request.get<{ records: ChatMessage[]; total: number }>(`/api/v1/chat/messages/${conversationId}`, {
    params: { size }
  })

/** 哈希链验证 */
export const verifyChain = (conversationId: string) =>
  request.get(`/api/v1/chat/verify/${conversationId}`)
