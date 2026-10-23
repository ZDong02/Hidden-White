<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button, DataTable, Field, Metric, Pagination, Panel, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, PermissionButton } from '@/components/business'
import { operationApi, type OperationTrafficRow, type OperationTrafficToday } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

const today = ref<OperationTrafficToday | null>(null)
const rows = ref<OperationTrafficRow[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, startDate: '', endDate: '' })

const columns: Column[] = [
  { key: 'statDate', title: '日期', width: 120 },
  { key: 'orderCount', title: '订单数', align: 'right', width: 90 },
  { key: 'revenue', title: '营业额', align: 'right', width: 110 },
  { key: 'visitorCount', title: '客流', align: 'right', width: 90 },
  { key: 'closedMinutes', title: '打烊时长', align: 'right', width: 100 },
  { key: 'adjusted', title: '调整状态', width: 110 },
  { key: 'adjustReason', title: '调整原因', minWidth: 160 },
  { key: 'ops', title: '操作', width: 90, align: 'center' },
]

async function loadToday() {
  try {
    today.value = await operationApi.traffic.today()
  } catch (error) {
    toastError(error, '今日实时数据加载失败')
  }
}

async function load() {
  loading.value = true
  try {
    const page = await operationApi.traffic.list({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
    })
    rows.value = page.rows
    total.value = page.total
  } catch (error) {
    toastError(error, '日流量数据加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function resetFilter() {
  query.startDate = ''
  query.endDate = ''
  search()
}

// ---- 调整 ----

interface AdjustForm { date: string; orderCount: number; revenue: number; visitorCount: number; reason: string }
const dialog = ref(false)
const saving = ref(false)
const form = reactive<AdjustForm>({ date: '', orderCount: 0, revenue: 0, visitorCount: 0, reason: '' })
const errors = ref<Partial<Record<keyof AdjustForm, string>>>({})
const editing = ref<OperationTrafficRow | null>(null)

function openAdjust(row: OperationTrafficRow) {
  editing.value = row
  Object.assign(form, {
    date: row.statDate,
    orderCount: row.orderCount,
    revenue: Number(row.revenue),
    visitorCount: row.visitorCount,
    reason: '',
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    orderCount: [required('请输入订单数')],
    revenue: [required('请输入营业额')],
    visitorCount: [required('请输入客流数')],
    reason: [required('调整原因为必填项')],
  })
  if (Object.keys(errors.value).length) return
  if (form.orderCount < 0 || form.visitorCount < 0 || form.revenue < 0) {
    pushToast('数值不能为负数', 'danger')
    return
  }
  saving.value = true
  try {
    await operationApi.traffic.adjust(form.date, {
      orderCount: Math.round(form.orderCount),
      revenue: Number(form.revenue.toFixed(2)),
      visitorCount: Math.round(form.visitorCount),
      reason: form.reason.trim(),
    })
    pushToast(`${form.date} 的日流量已调整`)
    dialog.value = false
    await Promise.all([load(), loadToday()])
  } catch (error) {
    toastError(error, '日流量调整失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => { loadToday(); load() })
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">日流统计</h2>
        <p class="page-desc">营业中的数据实时只读；打烊（或跨日）后自动结算冻结，方可人工调整并留痕。</p>
      </div>
    </div>

    <Panel title="今日实时" class="today-panel">
      <div v-if="today" class="today-body">
        <div class="today-metrics">
          <Metric :value="today.orderCount" label="订单数" unit="单" />
          <Metric :value="today.revenue" :decimals="2" label="营业额" unit="¥" />
          <Metric :value="today.visitorEstimate" label="客流（估算）" unit="人" />
          <Metric :value="today.inProgressCount" label="进行中" unit="单" />
        </div>
        <Tag :tone="today.settled ? 'neutral' : 'success'">{{ today.settled ? '已结算冻结' : '实时统计中' }}</Tag>
      </div>
      <p v-else class="muted">正在读取今日数据…</p>
    </Panel>

    <Panel title="历史日流量">
      <div class="filter-bar">
        <TextInput v-model="query.startDate" type="date" />
        <span class="muted">至</span>
        <TextInput v-model="query.endDate" type="date" />
        <Button variant="secondary" @click="search">查询</Button>
        <Button variant="ghost" @click="resetFilter">重置</Button>
      </div>

      <DataTable :columns="columns" :data="rows" row-key="statDate" :loading="loading" empty-text="所选区间暂无结算数据">
        <template #cell-revenue="{ row }">¥ {{ row.revenue }}</template>
        <template #cell-closedMinutes="{ row }">{{ row.closedMinutes }} 分钟</template>
        <template #cell-adjusted="{ row }">
          <Tag :tone="row.adjusted ? 'warning' : 'neutral'">{{ row.adjusted ? '已调整' : '原始值' }}</Tag>
        </template>
        <template #cell-adjustReason="{ row }">
          <span v-if="row.adjusted" :title="row.adjustReason">
            {{ row.originOrderCount !== null ? `${row.originOrderCount} → ${row.orderCount}` : '' }}
            {{ row.adjustReason }}
          </span>
          <span v-else class="muted">—</span>
        </template>
        <template #cell-ops="{ row }">
          <PermissionButton
            v-if="row.settled"
            perm="operation:traffic:edit"
            icon-only
            icon="edit"
            label="调整"
            size="sm"
            @click="openAdjust(row)"
          />
          <span v-else class="muted" title="打烊后自动结算，结算后可调整">未结算</span>
        </template>
      </DataTable>

      <Pagination
        v-model:page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        @update:page="load"
        @update:page-size="search"
      />
    </Panel>

    <FormDialog :open="dialog" :title="`调整 ${form.date} 日流量`" :saving="saving" @close="dialog = false" @submit="submit">
      <p v-if="editing" class="origin-hint">
        原始值（自动汇总，只读）：订单 {{ editing.originOrderCount ?? editing.orderCount }} ·
        营业额 ¥{{ editing.originRevenue ?? editing.revenue }} ·
        客流 {{ editing.originVisitorCount ?? editing.visitorCount }}
      </p>
      <Field label="订单数" required :error="errors.orderCount">
        <TextInput v-model="form.orderCount" type="number" :invalid="!!errors.orderCount" />
      </Field>
      <Field label="营业额（¥）" required :error="errors.revenue">
        <TextInput v-model="form.revenue" type="number" step="0.01" :invalid="!!errors.revenue" />
      </Field>
      <Field label="客流数" required :error="errors.visitorCount" hint="自动汇总是下单会员去重数，可按实际到店人数修正">
        <TextInput v-model="form.visitorCount" type="number" :invalid="!!errors.visitorCount" />
      </Field>
      <Field label="调整原因" required :error="errors.reason">
        <TextInput v-model="form.reason" placeholder="如：含未录入的堂食散单" :invalid="!!errors.reason" />
      </Field>
    </FormDialog>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.today-panel { margin-bottom: 16px; }
.today-body { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.today-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; flex: 1; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.filter-bar :deep(input) { min-width: 150px; }
.origin-hint { font-size: 12px; color: var(--ink-soft, #6f675c); margin-bottom: 12px; }
</style>
