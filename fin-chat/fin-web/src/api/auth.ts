import request from './request'

export interface LoginResponse {
  userId: number
  accessToken: string
  refreshToken: string
  expiresIn: number
  realNameStatus: number
  riskLevel: number
  nickname?: string
  avatar?: string
  realNameRequired: boolean
}

export interface SmsSendResponse {
  expireSeconds: number
  bizRef: string
}

/** 手机号 + 短信登录 */
export const loginByMobile = (data: { mobile: string; smsCode: string; deviceId: string }) =>
  request.post<LoginResponse>('/api/v1/auth/mobile/login', data)

/** 微信小程序登录 */
export const loginByWxMini = (data: {
  code: string
  encryptedData?: string
  iv?: string
  nickname?: string
  avatar?: string
  deviceId: string
}) => request.post<LoginResponse>('/api/v1/auth/wx/mini/login', data)

/** 下发短信验证码 */
export const sendSms = (data: { mobile: string; biz: string }) =>
  request.post<SmsSendResponse>('/api/v1/auth/sms/send', data)

/** 刷新 token */
export const refresh = (refreshToken: string) =>
  request.post<{ accessToken: string; expiresIn: number }>('/api/v1/auth/refresh', { refreshToken })

/** 登出 */
export const logout = () => request.post('/api/v1/auth/logout')

/** 获取当前用户 (实际从 store 取) */
export const fetchProfile = () => request.get('/api/v1/auth/profile')
