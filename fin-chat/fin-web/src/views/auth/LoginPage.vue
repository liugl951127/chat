<template>
  <div class="login-bg">
    <div class="login-card">
      <div class="login-title">FinChat</div>
      <div class="login-subtitle">金融合规聊天与交易系统</div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="手机号登录" name="mobile">
          <el-form @submit.prevent="onLogin" :model="form" size="large">
            <el-form-item>
              <el-input v-model="form.mobile" placeholder="请输入手机号" maxlength="11">
                <template #prefix><el-icon><Iphone /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <el-input v-model="form.smsCode" placeholder="6 位短信验证码" maxlength="6">
                <template #prefix><el-icon><Lock /></el-icon></template>
                <template #append>
                  <el-button :disabled="countdown > 0" @click="onSendSms" :loading="sending">
                    {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>

            <el-button type="primary" size="large" style="width:100%" @click="onLogin" :loading="loading">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="微信小程序" name="wx">
          <div class="wx-hint">
            <el-icon size="48" color="#67c23a"><ChatDotRound /></el-icon>
            <p>请在微信小程序中扫描二维码登录</p>
            <p class="text-muted">沙箱演示: 点击下方按钮模拟登录</p>
            <el-button type="primary" @click="onWxLogin" :loading="loading" style="width:100%; margin-top: 16px;">
              模拟小程序登录
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="agreement">
        登录即同意
        <a href="#">《隐私政策》</a> 和 <a href="#">《服务协议》</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { sendSms as sendSmsApi } from '@/api/auth'
import { getDeviceId } from '@/composables/useDeviceId'

const router = useRouter()
const auth = useAuthStore()

const tab = ref<'mobile' | 'wx'>('mobile')
const form = reactive({ mobile: '', smsCode: '' })
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer: number | null = null

const onSendSms = async () => {
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const resp = await sendSmsApi({ mobile: form.mobile, biz: 'LOGIN' })
    ElMessage.success(`验证码已发送, ${resp.data.expireSeconds} 秒内有效`)
    countdown.value = resp.data.expireSeconds
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
    // 沙箱: 后端会 log 验证码, 开发期可见
    console.log('[沙箱] 验证码: ' + (resp.data as any).code)
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const onLogin = async () => {
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) return ElMessage.warning('手机号格式错误')
  if (!/^\d{6}$/.test(form.smsCode)) return ElMessage.warning('请输入 6 位验证码')
  loading.value = true
  try {
    await auth.loginByMobile(form.mobile, form.smsCode, getDeviceId())
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    // 错误已 toast
  } finally {
    loading.value = false
  }
}

const onWxLogin = async () => {
  loading.value = true
  try {
    // 沙箱: 直接传 demo code
    await auth.loginByWxMini({
      code: 'sandbox-demo-code',
      deviceId: getDeviceId(),
      nickname: '微信用户',
      avatar: ''
    })
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.wx-hint { text-align: center; padding: 24px 0; }
.wx-hint p { margin-top: 12px; color: #606266; }
.wx-hint .text-muted { color: #909399; font-size: 12px; }
.agreement {
  text-align: center; font-size: 12px; color: #909399; margin-top: 16px;
}
</style>
