import request from './request'

export interface AuditEvent {
  traceId: string
  userId: number
  eventType: string
  targetType?: string
  targetId?: string
  action?: string
  result: string
  ip?: string
  deviceId?: string
  ts: string
  prevHash?: string
  currHash?: string
}

/** 按用户查询审计 */
export const queryByUser = (userId: number, limit = 50) =>
  request.get<AuditEvent[]>(`/api/v1/audit/query/user/${userId}`, { params: { limit } })

/** 按事件类型查询 */
export const queryByEvent = (eventType: string, limit = 50) =>
  request.get<AuditEvent[]>(`/api/v1/audit/query/event/${eventType}`, { params: { limit } })

/** 哈希链验证 */
export const verifyChain = () =>
  request.get<{ okCount: number; failCount: number; totalCount: number }>('/api/v1/audit/verify/chain')

/** 统计数据 */
export const auditStats = () => request.get<{ totalEvents: number }>('/api/v1/audit/stats')
