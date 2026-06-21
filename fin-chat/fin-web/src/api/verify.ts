import request from './request'

export interface VerifyEntity {
  type: string
  value: string
  context?: string
}

export interface VerifyRef {
  id: string
  title: string
  summary: string
  url?: string
  ts: number
}

export interface VerifyResult {
  type: string        // ENT / STOCK / POLICY / PRODUCT
  entity: VerifyEntity
  success: boolean
  summary: string
  references: VerifyRef[]
  riskScore: number
  costMs: number
  errorMessage?: string
}

/** 一站式核查 */
export const verify = (text: string) =>
  request.post<{ results: VerifyResult[]; totalMs: number }>('/api/v1/chat/verify', { text })

/** 单纯提取实体 */
export const extract = (text: string) =>
  request.post<{ entities: VerifyEntity[] }>('/api/v1/chat/verify/extract', { text })
