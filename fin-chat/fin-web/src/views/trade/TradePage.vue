<template>
  <div class="page-container">
    <h2 style="margin-bottom: 16px;">交易</h2>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card">
          <h3 style="margin-bottom: 16px;">产品列表</h3>
          <el-table :data="products" border>
            <el-table-column prop="code" label="代码" width="100" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="riskLevel" label="风险" width="80">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)">{{ row.riskLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nav" label="净值" width="100">
              <template #default="{ row }">¥{{ row.nav.toFixed(4) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="onBuy(row)">买入</el-button>
                <el-button size="small" @click="onSell(row)">卖出</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="10">
        <div class="card">
          <h3 style="margin-bottom: 16px;">最近交易</h3>
          <el-timeline v-if="trades.length">
            <el-timeline-item v-for="t in trades" :key="t.tradeId" :timestamp="t.ts">
              <div :class="['trade', t.status === 'SUCCESS' ? 'ok' : 'fail']">
                <div><b>{{ t.product }}</b> · {{ t.bizType }} · ¥{{ t.amount }}</div>
                <div class="meta">
                  <span :class="`tag tag-${t.status.toLowerCase()}`">{{ t.status }}</span>
                  <span v-if="t.coreSerial">流水: {{ t.coreSerial }}</span>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div v-else style="color: #909399; padding: 24px 0; text-align: center;">
            暂无交易
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 交易确认弹窗 -->
    <el-dialog v-model="dialogVisible" title="交易确认" width="500px"
               :close-on-click-modal="false" :close-on-press-escape="false">
      <div v-if="step === 'sms'">
        <p>系统已向您绑定的手机发送 6 位短信验证码, 请在 60 秒内输入。</p>
        <el-input v-model="smsCode" maxlength="6" placeholder="请输入验证码"
                  size="large" style="margin: 16px 0;" />
        <el-countdown v-if="countdown > 0" :value="Date.now() + countdown * 1000"
                      format="mm:ss" @finish="countdown = 0" />
        <el-button v-else link @click="onResend">重新获取</el-button>
      </div>

      <div v-else-if="step === 'review'">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="产品">{{ pending.product?.name }}</el-descriptions-item>
          <el-descriptions-item label="代码">{{ pending.product?.code }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ pending.bizType }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ pending.amount.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="风险">
            <el-tag :type="riskTag(pending.product?.riskLevel)">{{ pending.product?.riskLevel }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert type="warning" :closable="false" show-icon
                  title="适当性匹配" description="本产品与您的风险等级匹配 ✓" style="margin: 16px 0;" />
        <el-checkbox v-model="confirmed">
          我已阅读并理解
          <a href="#">《风险揭示书》</a> 和
          <a href="#">《产品合同》</a>
        </el-checkbox>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="step === 'sms'" type="primary"
                   :disabled="smsCode.length !== 6" :loading="loading"
                   @click="step = 'review'">验证并继续</el-button>
        <el-button v-if="step === 'review'" type="primary"
                   :disabled="!confirmed" :loading="loading"
                   @click="onSubmit">提交交易</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { initiateTrade, confirmTrade } from '@/api/trade'
import { submitRiskTest, getRiskTest } from '@/api/trade'

const products = ref([
  { code: 'FUND001', name: '稳健理财 · 货币基金', riskLevel: 'C1', nav: 1.0234, category: 'FUND' },
  { code: 'FUND002', name: '平衡配置 · 混合基金', riskLevel: 'C3', nav: 2.1456, category: 'FUND' },
  { code: 'STOCK001', name: '沪深 300 ETF', riskLevel: 'C4', nav: 3.9876, category: 'STOCK' },
  { code: 'BOND001', name: '国债逆回购 7 天', riskLevel: 'C2', nav: 1.0000, category: 'BOND' }
])

const trades = ref<any[]>([])

const dialogVisible = ref(false)
const step = ref<'sms' | 'review'>('sms')
const smsCode = ref('')
const countdown = ref(60)
const loading = ref(false)
const confirmed = ref(false)
const pending = reactive<{
  product: any
  bizType: string
  amount: number
  tradeId: string
}>({ product: null, bizType: '', amount: 0, tradeId: '' })

const riskTag = (l: string) => ({
  C1: 'success', C2: 'success', C3: 'warning', C4: 'danger', C5: 'danger'
}[l] || 'info')

const onBuy = (p: any) => openDialog(p, 'BUY')
const onSell = (p: any) => openDialog(p, 'SELL')

const openDialog = async (p: any, bizType: string) => {
  pending.product = p
  pending.bizType = bizType
  pending.amount = p.nav * 100  // 沙箱: 默认 100 份
  pending.tradeId = ''
  step.value = 'sms'
  smsCode.value = ''
  confirmed.value = false
  countdown.value = 60
  dialogVisible.value = true

  // 1. 调 initiate, 触发短信下发
  loading.value = true
  try {
    const resp = await initiateTrade({
      productCode: p.code,
      bizType: bizType as any,
      amount: pending.amount
    })
    pending.tradeId = resp.data.tradeId
    ElMessage.info(`交易已发起 (${resp.data.tradeId}), 短信已发送`)
  } catch (e: any) {
    ElMessage.error(e?.message || '发起失败')
    dialogVisible.value = false
  } finally {
    loading.value = false
  }
}

const onResend = () => {
  if (pending.product) openDialog(pending.product, pending.bizType)
}

const onSubmit = async () => {
  loading.value = true
  try {
    const resp = await confirmTrade({ tradeId: pending.tradeId, smsCode: smsCode.value })
    ElMessage.success('交易成功!')
    trades.value.unshift({
      tradeId: resp.data.tradeId,
      product: pending.product.name,
      bizType: pending.bizType,
      amount: pending.amount,
      status: resp.data.status,
      coreSerial: resp.data.coreSerial,
      ts: new Date().toLocaleString('zh-CN')
    })
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 沙箱: 可选加载用户风险等级
  getRiskTest().then(r => {
    console.log('[风险测评]', r.data)
  }).catch(() => {})
})
</script>

<style scoped>
.trade { font-size: 13px; }
.trade.ok { color: #67c23a; }
.trade.fail { color: #f56c6c; }
.trade .meta { display: flex; gap: 8px; align-items: center; margin-top: 4px; font-size: 12px; color: #909399; }
.tag { padding: 1px 6px; border-radius: 3px; font-size: 11px; }
.tag-success { background: #f0f9eb; color: #67c23a; }
.tag-fail { background: #fef0f0; color: #f56c6c; }
</style>
