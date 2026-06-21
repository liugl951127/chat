<template>
  <div class="page-container">
    <h2 style="margin-bottom: 16px;">实名认证</h2>

    <el-steps :active="step" finish-status="success" align-center>
      <el-step title="填写资料" />
      <el-step title="活体认证" />
      <el-step title="提交审核" />
      <el-step title="完成" />
    </el-steps>

    <div class="card" style="margin-top: 24px;">
      <!-- Step 1: 填写资料 -->
      <div v-if="step === 0">
        <h3>个人信息</h3>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" placeholder="请输入身份证上的姓名" maxlength="20" />
          </el-form-item>
          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="form.idCard" placeholder="18 位身份证号" maxlength="18" />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="form.mobile" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="验证码" prop="smsCode">
            <el-input v-model="form.smsCode" placeholder="6 位验证码" maxlength="6" style="width: 60%;">
              <template #append>
                <el-button :disabled="countdown > 0" @click="onSendSms" :loading="sending">
                  {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="form.agreed">
              我已阅读并同意
              <a href="#">《个人信息收集授权书》</a> 和
              <a href="#">《人脸识别服务协议》</a>
            </el-checkbox>
          </el-form-item>
          <el-button type="primary" size="large" @click="onNext" :loading="loading">
            下一步: 活体认证
          </el-button>
        </el-form>
      </div>

      <!-- Step 2: 活体认证 -->
      <div v-if="step === 1" style="text-align: center;">
        <el-icon size="80" color="#409eff"><Camera /></el-icon>
        <h3 style="margin-top: 16px;">活体认证</h3>
        <p style="color: #909399; margin: 16px 0;">
          请正对屏幕, 缓慢眨眼并保持光线充足
        </p>
        <div class="face-box">
          <div class="face-circle"></div>
          <p class="status">{{ faceStatus }}</p>
        </div>
        <div style="margin-top: 24px;">
          <el-button @click="step = 0">上一步</el-button>
          <el-button type="primary" @click="onFaceAuth" :loading="loading">
            开始认证
          </el-button>
        </div>
      </div>

      <!-- Step 3: 提交审核 -->
      <div v-if="step === 2" style="text-align: center;">
        <el-icon size="80" color="#67c23a"><Loading /></el-icon>
        <h3 style="margin-top: 16px;">提交审核中</h3>
        <p style="color: #909399; margin: 16px 0;">系统正在验证您的身份信息...</p>
      </div>

      <!-- Step 4: 完成 -->
      <div v-if="step === 3" style="text-align: center;">
        <el-result icon="success" title="实名认证成功" sub-title="您的账户已升级为强实名用户">
          <template #extra>
            <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
          </template>
        </el-result>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, Loading } from '@element-plus/icons-vue'
import { sendSms } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const step = ref(0)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer: number | null = null

const formRef = ref()
const form = reactive({
  realName: '',
  idCard: '',
  mobile: '',
  smsCode: '',
  agreed: false
})

const rules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^[1-9]\d{5}(?:18|19|20)\d{2}(?:0\d|1[0-2])(?:[0-2]\d|3[01])\d{3}[\dXx]$/, message: '身份证号格式错误', trigger: 'blur' }
  ],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ],
  smsCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const faceStatus = ref('准备中...')

const onSendSms = async () => {
  if (!/^1[3-9]\d{9}$/.test(form.mobile)) return ElMessage.warning('手机号格式错误')
  sending.value = true
  try {
    const resp = await sendSms({ mobile: form.mobile, biz: 'REAL_NAME' })
    ElMessage.success(`验证码已发送 (${resp.data.expireSeconds}s)`)
    countdown.value = resp.data.expireSeconds
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer) { clearInterval(timer); timer = null }
    }, 1000)
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const onNext = async () => {
  await formRef.value.validate()
  if (!form.agreed) return ElMessage.warning('请勾选协议')
  if (!/^\d{6}$/.test(form.smsCode)) return ElMessage.warning('请输入 6 位验证码')

  loading.value = true
  try {
    // 沙箱: 跳过真实验证, 直接到活体认证
    await new Promise(r => setTimeout(r, 500))
    step.value = 1
    faceStatus.value = '就绪, 点击开始认证'
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

const onFaceAuth = async () => {
  loading.value = true
  faceStatus.value = '检测中...'
  try {
    // 沙箱: 模拟 3 步活体检测
    await new Promise(r => setTimeout(r, 800))
    faceStatus.value = '请眨眼'
    await new Promise(r => setTimeout(r, 800))
    faceStatus.value = '请张嘴'
    await new Promise(r => setTimeout(r, 800))
    faceStatus.value = '认证通过 ✓'
    await new Promise(r => setTimeout(r, 500))
    step.value = 2
    await new Promise(r => setTimeout(r, 1500))
    auth.realNameStatus = 2  // 强实名
    step.value = 3
  } catch (e: any) {
    ElMessage.error(e?.message || '认证失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.face-box {
  margin: 24px auto;
  width: 240px;
  height: 240px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
}
.face-circle {
  width: 200px;
  height: 200px;
  border: 3px dashed #409eff;
  border-radius: 50%;
  animation: pulse 2s infinite;
}
.status {
  position: absolute;
  bottom: 0;
  color: #606266;
  font-size: 14px;
}
@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.7; }
  100% { transform: scale(1); opacity: 1; }
}
</style>
