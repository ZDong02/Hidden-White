<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button, Field, Panel, TextInput } from '@/components/ui'
import { coffeeApi, type CoffeeStoreRow } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import { required, pattern, validate } from '@/utils/validate'
import { TIME_PATTERN } from '@/data'

const store = ref<CoffeeStoreRow | null>(null)
const loading = ref(false)
const saving = ref(false)
const form = reactive<CoffeeStoreRow>({
  name: '', address: '', shortAddress: '', latitude: 0, longitude: 0,
  distance: '', openingTime: '08:00', closingTime: '20:00',
})
const errors = ref<Partial<Record<keyof CoffeeStoreRow, string>>>({})

async function load() {
  loading.value = true
  try {
    const data = await coffeeApi.store.get()
    store.value = data
    Object.assign(form, data)
  } catch (error) {
    toastError(error, '门店信息加载失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  errors.value = validate(form, {
    name: [required('请输入门店名称')],
    address: [required('请输入门店地址')],
    openingTime: [required('请输入开门时间'), pattern(TIME_PATTERN, '格式为 HH:mm，如 08:00')],
    closingTime: [required('请输入打烊时间'), pattern(TIME_PATTERN, '格式为 HH:mm，如 20:00')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await coffeeApi.store.update({
      ...form,
      name: form.name.trim(),
      address: form.address.trim(),
      shortAddress: form.shortAddress.trim(),
      latitude: Number(form.latitude),
      longitude: Number(form.longitude),
      distance: form.distance.trim(),
      openingTime: form.openingTime.slice(0, 5),
      closingTime: form.closingTime.slice(0, 5),
    })
    pushToast('门店信息已保存，点单端实时生效')
    await load()
  } catch (error) {
    toastError(error, '门店信息保存失败')
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
        <h2 class="page-title">门店设置</h2>
        <p class="page-desc">门店名称、地址、坐标与营业时间，点单端首页直接读取。</p>
      </div>
    </div>
  
    <Panel title="门店信息" class="store-panel">
      <form v-if="form.name || loading" @submit.prevent="submit">
        <div class="store-grid">
          <Field label="门店名称" required :error="errors.name">
            <TextInput v-model="form.name" :invalid="!!errors.name" />
          </Field>
          <Field label="距离标签">
            <TextInput v-model="form.distance" placeholder="320 米" />
          </Field>
          <Field label="完整地址" required :error="errors.address" span>
            <TextInput v-model="form.address" :invalid="!!errors.address" />
          </Field>
          <Field label="短地址（地图标注）" span>
            <TextInput v-model="form.shortAddress" />
          </Field>
          <Field label="纬度" required>
            <TextInput v-model="form.latitude" type="number" step="0.0000001" />
          </Field>
          <Field label="经度" required>
            <TextInput v-model="form.longitude" type="number" step="0.0000001" />
          </Field>
          <Field label="开门时间" required :error="errors.openingTime">
            <TextInput v-model="form.openingTime" placeholder="08:00" :invalid="!!errors.openingTime" />
          </Field>
          <Field label="打烊时间" required :error="errors.closingTime">
            <TextInput v-model="form.closingTime" placeholder="20:00" :invalid="!!errors.closingTime" />
          </Field>
        </div>
        <div class="store-actions">
          <Button variant="primary" type="submit" :loading="saving">保存修改</Button>
        </div>
      </form>
    </Panel>
  </div>
</template>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.store-panel { max-width: 860px; }
.store-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.store-actions { margin-top: 18px; }
</style>
