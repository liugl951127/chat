import request from './request'

export interface TradeProduct {
  code: string
  name: string
  riskLevel: 'C1' | 'C2' | 'C3' | 'C4' | 'C5'
  nav: number
  category: string
}

/** 发起交易 (返回 tradeId + 短信已发) */
export const initiateTrade = (data: {
  productCode: string
  bizType: 'BUY' | 'SELL' | 'SUBSCRIBE' | 'REDEEM'
  amount: number
}) => request.post<{ tradeId: string; expireSeconds: number; codeHint: string }>(
  '/api/v1/trade/initiate', data
)

/** 确认交易 (校验短信) */
export const confirmTrade = (data: { tradeId: string; smsCode: string }) =>
  request.post<{
    tradeId: string
    status: string
    coreSerial: string
    signature: string
    algorithm: string
  }>('/api/v1/trade/confirm', data)

/** 风险测评 */
export const submitRiskTest = (answers: number[]) =>
  request.post('/api/v1/compliance/risk-test', { answers })

export const getRiskTest = () => request.get('/api/v1/compliance/risk-test')
