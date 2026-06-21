import axios, { AxiosError, AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const instance = axios.create({
  baseURL: '/',
  timeout: 15000,
})

// 请求拦截
instance.interceptors.request.use((cfg) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    cfg.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  // 链路追踪
  cfg.headers['X-Trace-Id'] = crypto.randomUUID()
  return cfg
})

// 响应拦截
instance.interceptors.response.use(
  (resp) => resp.data,
  async (error: AxiosError<any>) => {
    const { response, config } = error
    const auth = useAuthStore()

    // 401 自动刷新
    if (response?.status === 401 && !(config as any)._retry) {
      ;(config as any)._retry = true
      try {
        await auth.silentRefresh()
        ;(config as AxiosRequestConfig).headers!.Authorization = `Bearer ${auth.accessToken}`
        return instance(config!)
      } catch {
        auth.logout()
        location.href = '/login'
      }
    }

    const msg = response?.data?.message || '请求失败'
    if (response?.status !== 401) {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default instance
