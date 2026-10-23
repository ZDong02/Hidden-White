<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button, Field, Panel, Switch, TextInput } from '@/components/ui'
import { operationApi, type OperationDayHours } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import { TIME_PATTERN, WEEK_NAMES } from '@/data'

const loading = ref(false)
const saving = ref(false)

const defaults = reactive({ defaultOpen: '08:00', defaultClose: '20:00' })
const days = reactive<OperationDayHours[]>([])

const errors = ref<Record<string, string>>({})

function dayError(index: number, field: 'openTime' | 'closeTime') {
  return errors.value[`days.${index}.${field}`] ?? ''
}

async function load() {
  loading.value = true
  try {
    const data = await operationApi.hours.get()
    defaults.defaultOpen = data.defaultOpen
    defaults.defaultClose = data.defaultClose
    days.splice(0, days.length, ...data.days.map(day => ({ ...day })))
  } catch (error) {
    toastError(error, '运营时间加载失败')
  } finally {
    loading.value = false
  }
}

/** 前端先做一轮与后端一致的校验（HH:mm、开门早于打烊），再整批提交 */
function validateAll() {
  const found: Record<string, string> = {}
  const checkTime = (value: string, key: string, label: string) => {
    if (!TIME_PATTERN.test(value.trim())) found[key] = `${label}格式为 HH:mm，如 08:00`
  }
  checkTime(defaults.defaultOpen, 'defaultOpen', '默认开门时间')
  checkTime(defaults.defaultClose, 'defaultClose', '默认打烊时间')
  if (!found.defaultOpen && !found.defaultClose
      && defaults.defaultOpen.trim() >= defaults.defaultClose.trim()) {
    found.defaultClose = '打烊时间必须晚于开门时间（不支持跨夜）'
  }
  days.forEach((day, index) => {
    checkTime(day.openTime, `days.${index}.openTime`, `${WEEK_NAMES[day.dayOfWeek]}开门时间`)
    checkTime(day.closeTime, `days.${index}.closeTime`, `${WEEK_NAMES[day.dayOfWeek]}打烊时间`)
    if (!day.closed && !found[`days.${index}.openTime`] && !found[`days.${index}.closeTime`]
        && day.openTime.trim() >= day.closeTime.trim()) {
      found[`days.${index}.closeTime`] = '打烊时间必须晚于开门时间（不支持跨夜）'
    }
  })
  errors.value = found
  return Object.keys(found).length === 0
}

async function submit() {
  if (!validateAll()) {
    pushToast('请先修正标红的时段配置', 'danger')
    return
  }
  saving.value = true
  try {
    await operationApi.hours.update({
      defaultOpen: defaults.defaultOpen.trim(),
      defaultClose: defaults.defaultClose.trim(),
      days: days.map(day => ({
        dayOfWeek: day.dayOfWeek,
        openTime: day.openTime.trim(),
        closeTime: day.closeTime.trim(),
        closed: day.closed,
      })),
    })
    pushToast('运营时间已保存，点单端实时生效')
    await load()
  } catch (error) {
    toastError(error, '运营时间保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">运营时间</h2>
        <p class="page-desc">默认时段为全局兜底；按星期的配置优先生效，勾选「例休」当天全天打烊。</p>
      </div>
      <Button variant="primary" :loading="saving" @click="submit">保存全部</Button>
    </div>

    <form v-if="days.length || loading" @submit.prevent="submit">
      <Panel title="默认时段（每天相同的小店用这个就够）" class="hours-panel">
        <div class="default-grid">
          <Field label="开门时间" required :error="errors.defaultOpen">
            <TextInput v-model="defaults.defaultOpen" placeholder="08:00" :invalid="!!errors.defaultOpen" />
          </Field>
          <Field label="打烊时间" required :error="errors.defaultClose">
            <TextInput v-model="defaults.defaultClose" placeholder="20:00" :invalid="!!errors.defaultClose" />
          </Field>
        </div>
      </Panel>

      <Panel title="按星期配置（优先生效）" class="hours-panel">
        <div class="week-list">
          <div v-for="(day, index) in days" :key="day.dayOfWeek" :class="['week-row', { 'is-off': day.closed }]">
            <span class="week-name">{{ WEEK_NAMES[day.dayOfWeek] }}</span>
            <Field label="开门" :error="dayError(index, 'openTime')">
              <TextInput v-model="day.openTime" placeholder="08:00" :invalid="!!dayError(index, 'openTime')" />
            </Field>
            <Field label="打烊" :error="dayError(index, 'closeTime')">
              <TextInput v-model="day.closeTime" placeholder="20:00" :invalid="!!dayError(index, 'closeTime')" />
            </Field>
            <Field label="例休" group>
              <Switch v-model="day.closed" :label="day.closed ? '全天休息' : '正常营业'" />
            </Field>
          </div>
        </div>
        <p class="hours-hint">人工打烊中的店铺不受时段影响：即使时段覆盖当前时刻，仍显示打烊，需到「营业状态」页恢复。</p>
        <div class="hours-actions">
          <Button variant="primary" type="submit" :loading="saving">保存全部</Button>
        </div>
      </Panel>
    </form>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.hours-panel { max-width: 860px; margin-bottom: 16px; }
.default-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.week-list { display: flex; flex-direction: column; gap: 4px; }
.week-row {
  display: grid;
  grid-template-columns: 88px 1fr 1fr 200px;
  gap: 12px;
  align-items: start;
  padding: 10px 0;
  border-bottom: 1px solid var(--line, rgba(0, 0, 0, 0.06));
  transition: opacity 0.2s ease;
}
.week-row:last-child { border-bottom: none; }
.week-row.is-off { opacity: 0.55; }
.week-name { font-weight: 600; font-size: 14px; line-height: 40px; }
.hours-hint { margin-top: 12px; font-size: 12px; color: var(--ink-soft, #6f675c); }
.hours-actions { margin-top: 14px; }
</style>
