<template>
  <div class="page-container">
    <h2 style="margin-bottom: 16px;">审计查询 (合规员视图)</h2>

    <div class="card">
      <el-form inline>
        <el-form-item label="用户ID">
          <el-input-number v-model="queryUserId" :min="1" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQueryUser" :loading="loading">查询</el-button>
          <el-button @click="onVerifyChain">验证哈希链</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <h4>最近事件</h4>
      <el-table :data="events" border>
        <el-table-column prop="traceId" label="TraceId" width="200" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="eventType" label="事件" width="120" />
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column prop="result" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ts" label="时间" width="180" />
        <el-table-column prop="currHash" label="哈希" min-width="100">
          <template #default="{ row }">
            <code style="font-size: 11px;">{{ (row.currHash || '').substring(0, 16) }}...</code>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryByUser, verifyChain, auditStats } from '@/api/audit'

const queryUserId = ref<number>(1)
const loading = ref(false)
const events = ref<any[]>([])

const onQueryUser = async () => {
  loading.value = true
  try {
    const resp = await queryByUser(queryUserId.value, 50)
    events.value = resp.data || []
    ElMessage.success(`查到 ${events.value.length} 条事件`)
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const onVerifyChain = async () => {
  try {
    const resp = await verifyChain()
    const r = resp.data
    if (r.failCount === 0) {
      ElMessage.success(`哈希链完整 ✓ (${r.okCount}/${r.totalCount} 条)`)
    } else {
      ElMessage.error(`哈希链断裂! 失败 ${r.failCount} / ${r.totalCount}`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '验证失败')
  }
}
</script>
