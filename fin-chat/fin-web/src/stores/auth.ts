import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('access_token') || '')
  const refreshToken = ref(localStorage.getItem('refresh_token') || '')
  const userId = ref<number | null>(Number(localStorage.getItem('user_id')) || null)
  const realNameStatus = ref<number>(0)
  const riskLevel = ref<number>(1)
  const nickname = ref<string>('')
  const avatar = ref<string>('')

  const isLoggedIn = computed(() => !!accessToken.value)
  const realNameRequired = computed(() => realNameStatus.value < 2)

  const setLogin = (data: authApi.LoginResponse) => {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    userId.value = data.userId
    realNameStatus.value = data.realNameStatus
    riskLevel.value = data.riskLevel
    nickname.value = data.nickname || ''
    avatar.value = data.avatar || ''
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
    localStorage.setItem('user_id', String(data.userId))
  }

  const loginByMobile = async (mobile: string, smsCode: string, deviceId: string) => {
    const resp = await authApi.loginByMobile({ mobile, smsCode, deviceId })
    setLogin(resp.data)
    return resp.data
  }

  const loginByWxMini = async (params: {
    code: string
    encryptedData?: string
    iv?: string
    nickname?: string
    avatar?: string
    deviceId: string
  }) => {
    const resp = await authApi.loginByWxMini(params)
    setLogin(resp.data)
    return resp.data
  }

  const silentRefresh = async () => {
    if (!refreshToken.value) throw new Error('no refresh token')
    const resp = await authApi.refresh(refreshToken.value)
    accessToken.value = resp.data.accessToken
    localStorage.setItem('access_token', resp.data.accessToken)
    return resp.data.accessToken
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } catch (e) {
      // ignore
    }
    accessToken.value = ''
    refreshToken.value = ''
    userId.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user_id')
  }

  const fetchProfile = async () => {
    // 沙箱: store 已有信息, 实际可调 profile 接口
    return { userId: userId.value, realNameStatus: realNameStatus.value }
  }

  return {
    accessToken, refreshToken, userId, realNameStatus, riskLevel, nickname, avatar,
    isLoggedIn, realNameRequired,
    setLogin, loginByMobile, loginByWxMini, silentRefresh, logout, fetchProfile
  }
})
