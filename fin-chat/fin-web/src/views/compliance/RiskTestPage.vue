<template>
  <div class="page-container">
    <h2 style="margin-bottom: 16px;">风险测评</h2>

    <div class="card" v-if="!testResult">
      <p style="color: #909399; margin-bottom: 16px;">
        请根据您的实际情况选择答案 (1=完全不同意, 2=基本不同意, 3=中立, 4=基本同意, 5=完全同意)
      </p>

      <el-form>
        <el-form-item v-for="(q, i) in questions" :key="i" :label="`${i + 1}. ${q}`">
          <el-radio-group v-model="answers[i]">
            <el-radio-button v-for="n in 5" :key="n" :value="n">{{ n }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-button type="primary" size="large" :loading="loading" @click="onSubmit" style="margin-top: 16px;">
        提交测评
      </el-button>
    </div>

    <div class="card" v-else>
      <el-result :icon="resultIcon" :title="`您的风险等级: ${testResult.level}`" :sub-title="`得分: ${testResult.score} / 100`">
        <template #extra>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="等级">{{ testResult.level }}</el-descriptions-item>
            <el-descriptions-item label="得分">{{ testResult.score }}</el-descriptions-item>
            <el-descriptions-item label="测评时间">{{ formatTime(testResult.testedAt) }}</el-descriptions-item>
            <el-descriptions-item label="到期时间">{{ formatTime(testResult.expireAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-button type="primary" @click="testResult = null" style="margin-top: 16px;">重新测评</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { submitRiskTest } from '@/api/trade'

const questions = [
  '我能接受 10% 以下的本金亏损',
  '我计划投资 3 年以上',
  '我有稳定的收入来源',
  '我能接受本金可能全部亏损',
  '我倾向于高收益高风险的产品',
  '我有投资股票/基金的经验',
  '我了解基金/股票的基本知识',
  '我能在亏损时保持冷静, 不轻易赎回',
  '我的家庭没有大额债务',
  '我有充足的应急资金 (≥ 6 个月生活费)'
]

const answers = ref<number[]>(new Array(10).fill(0))
const loading = ref(false)
const testResult = ref<any>(null)

const resultIcon = computed(() => {
  const level = testResult.value?.level
  if (level === 'C1' || level === 'C2') return 'success'
  if (level === 'C3') return 'info'
  return 'warning'
})

const onSubmit = async () => {
  if (answers.value.some(a => a === 0)) {
    return ElMessage.warning('请回答所有题目')
  }
  loading.value = true
  try {
    const resp = await submitRiskTest(answers.value)
    testResult.value = resp.data
    ElMessage.success('测评完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (s: string) => s ? new Date(s).toLocaleString('zh-CN') : ''
</script>
