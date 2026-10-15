<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button, Field, Metric, Panel, Tag, TextInput } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import { operationApi, type OperationStatus } from '@/api'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { maxLength, validate } from '@/utils/validate'

const REASON_LABELS: Record<string, string> = {
  MANUAL: '人工打烊',
  DAY_OFF: '今日例休',
  NOT_YET_OPEN: '未到营业时间',
  AFTER_HOURS: '已过打烊时间',
}
const WEEK_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

const status = ref<OperationStatus | null>(null)
const loading = ref(false)
const switching = ref(false)
const savingNote = ref(false)
const noteForm = ref('')
const errors = ref<{ statusNote?: string }>({})

const isOpen = computed(() => status.value?.current.status === 'OPEN')
const reasonLabel = computed(() => REASON_LABELS[status.value?.current.reason ?? ''] ?? '')

async function load() {
  loading.value = true
  try {
    status.value = await operationApi.status.get()
    noteForm.value = status.value.statusNote
  } catch (error) {
    toastError(error, '营业状态加载失败')
  } finally {
    loading.value = false
  }
}

/** 打烊 / 恢复营业：目标状态取当前人工开关的反面；接口幂等，重复点击无副作用 */
async function toggle() {
  if (!status.value) return
  const targetClosed = !status.value.manualClosed
  const ok = await confirm({
    title: targetClosed ? '确认打烊' : '恢复营业',
    message: targetClosed
      ? '打烊后点单端将立即不可下单（进行中订单不受影响），并结算冻结今日流量。确认打烊吗？'
      : '恢复营业后，若当前处于运营时段内，点单端立即恢复下单。确认恢复吗？',
    confirmText: targetClosed ? '立即打烊' : '恢复营业',
    danger: targetClosed,
  })
  if (!ok) return
  switching.value = true
  try {
    status.value = await operationApi.status.update({
      manualClosed: targetClosed,
      statusNote: status.value.statusNote,
    })
    noteForm.value = status.value.statusNote
    pushToast(targetClosed ? '店铺已打烊' : '店铺已恢复营业')
  } catch (error) {
    toastError(error, '状态切换失败')
  } finally {
    switching.value = false
  }
}

/** 状态不变时仅更新打烊提示语 */
async function saveNote() {
  if (!status.value) return
  errors.value = validate({ statusNote: noteForm.value }, {
    statusNote: [maxLength(100, '提示语不能超过 100 字')],
  })
  if (Object.keys(errors.value).length) return
  savingNote.value = true
  try {
    status.value = await operationApi.status.update({
      manualClosed: status.value.manualClosed,
      statusNote: noteForm.value.trim(),
    })
    pushToast('打烊提示语已保存')
  } catch (error) {
    toastError(error, '提示语保存失败')
  } finally {
    savingNote.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">营业状态</h2>
        <p class="page-desc">人工打烊优先于按时段自动判断；切换后点单端下一次请求即生效。</p>
      </div>
      <PermissionButton
        v-if="status"
        perm="operation:status:edit"
        :variant="isOpen ? 'danger' : 'primary'"
        :loading="switching"
        @click="toggle"
      >{{ isOpen ? '立即打烊' : '恢复营业' }}</PermissionButton>
    </div>

    <div v-if="status" class="status-grid">
      <Panel title="当前状态" class="status-panel">
        <div class="status-main">
          <Tag :tone="isOpen ? 'success' : 'danger'">{{ isOpen ? '营业中' : '已打烊' }}</Tag>
          <p v-if="reasonLabel" class="status-reason">原因：{{ reasonLabel }}</p>
          <p class="status-note">{{ status.current.note || '一切正常，欢迎光临' }}</p>
          <p class="status-meta">
            人工开关：{{ status.manualClosed ? '已打烊' : '未打烊' }}
            · 今日时段：{{ status.today.closed ? '例休' : `${status.today.openTime}–${status.today.closeTime}` }}
          </p>
        </div>
      </Panel>

      <Panel title="今日实时" class="traffic-panel">
        <div class="traffic-metrics">
          <Metric :value="status.traffic.orderCount" label="今日订单" unit="单" />
          <Metric :value="status.traffic.revenue" :decimals="2" label="今日营业额" unit="¥" />
          <Metric :value="status.traffic.inProgressCount" label="进行中" unit="单" />
        </div>
        <p class="traffic-hint">
          {{ status.traffic.settled ? '今日已结算冻结，可到日流统计页调整' : '实时统计中，打烊后自动结算冻结' }}
        </p>
      </Panel>

      <Panel title="打烊提示语" class="note-panel">
        <form @submit.prevent="saveNote">
          <Field label="提示语文案" :error="errors.statusNote" hint="人工打烊时展示在点单端，留空用默认文案（最长 100 字）">
            <TextInput v-model="noteForm" placeholder="店庆休息，明日恢复" :invalid="!!errors.statusNote" />
          </Field>
          <div class="note-actions">
            <PermissionButton perm="operation:status:edit" variant="primary" :loading="savingNote" @click="saveNote">保存提示语</PermissionButton>
          </div>
        </form>
      </Panel>

      <Panel title="时段参考" class="hours-panel">
        <div class="hours-rows">
          <div class="hours-row">
            <span class="hours-day">今日 · {{ WEEK_NAMES[status.today.dayOfWeek] }}</span>
            <span class="muted">{{ status.today.closed ? '例休' : `${status.today.openTime} – ${status.today.closeTime}` }}</span>
          </div>
          <div class="hours-row">
            <span class="hours-day">明日 · {{ WEEK_NAMES[status.tomorrow.dayOfWeek] }}</span>
            <span class="muted">{{ status.tomorrow.closed ? '例休' : `${status.tomorrow.openTime} – ${status.tomorrow.closeTime}` }}</span>
          </div>
        </div>
        <p class="traffic-hint">按星期配置请到「运营时间」页修改。</p>
      </Panel>
    </div>

    <Panel v-if="loading && !status" title="加载中">
      <p class="muted">正在读取营业状态…</p>
    </Panel>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.status-main { display: flex; flex-direction: column; gap: 8px; padding: 6px 0; }
.status-reason { font-size: 13px; color: var(--ink-soft, #6f675c); }
.status-note { font-size: 15px; }
.status-meta { font-size: 12px; color: var(--ink-soft, #6f675c); }
.traffic-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 6px 0; }
.traffic-hint { margin-top: 10px; font-size: 12px; color: var(--ink-soft, #6f675c); }
.note-actions { margin-top: 14px; }
.hours-rows { display: flex; flex-direction: column; gap: 10px; padding: 6px 0; }
.hours-row { display: flex; justify-content: space-between; font-size: 14px; }
.hours-day { font-weight: 600; }
</style>
