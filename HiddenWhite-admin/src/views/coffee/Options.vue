<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Field, SectionLabel, Select, Switch, Tag, TextInput } from '@/components/ui'
import { FormDialog, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeOptionRow } from '@/api'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

/* 规格选项是三大类的小集合，按类型分组展示，不做服务端分页 */
const TYPE_LABEL: Record<string, string> = { SIZE: '杯型', MILK: '奶类', TEMPERATURE: '温度' }

const options = ref<CoffeeOptionRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    options.value = await coffeeApi.options.list()
  } catch (error) {
    toastError(error, '规格选项加载失败')
  } finally {
    loading.value = false
  }
}

const groups = computed(() =>
  Object.keys(TYPE_LABEL).map(type => ({ type, label: TYPE_LABEL[type], items: options.value.filter(o => o.type === type) })),
)

interface OptionForm { id: number | null; type: string; label: string; extraPrice: number | null; sortOrder: number; enabled: boolean }
const dialog = ref(false)
const saving = ref(false)
const form = reactive<OptionForm>({ id: null, type: 'SIZE', label: '', extraPrice: null, sortOrder: 0, enabled: true })
const errors = ref<Partial<Record<keyof OptionForm, string>>>({})

function openCreate(type: string) {
  Object.assign(form, { id: null, type, label: '', extraPrice: null, sortOrder: 0, enabled: true })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: CoffeeOptionRow) {
  Object.assign(form, { id: row.id, type: row.type, label: row.label, extraPrice: row.extraPrice, sortOrder: row.sortOrder, enabled: row.enabled })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    label: [required('请输入选项名称')],
    extraPrice: [required('请输入加价金额')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = { type: form.type, label: form.label.trim(), extraPrice: Number(form.extraPrice), sortOrder: form.sortOrder, enabled: form.enabled }
  try {
    if (form.id === null) {
      await coffeeApi.options.create(payload)
      pushToast('选项已新增')
    } else {
      await coffeeApi.options.update(form.id, payload)
      pushToast('选项已更新')
    }
    dialog.value = false
    await load()
  } catch (error) {
    toastError(error, '选项保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CoffeeOptionRow) {
  const ok = await confirm({ title: '删除选项', message: `确认删除${TYPE_LABEL[row.type]}「${row.label}」吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await coffeeApi.options.remove(row.id)
    pushToast('选项已删除')
    await load()
  } catch (error) {
    toastError(error, '选项删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">规格选项</h2>
        <p class="page-desc">杯型、奶类与温度决定点单端可选的规格和加价，改动实时生效。</p>
      </div>
      <PermissionButton perm="coffee:option:add" variant="primary" icon="plus" @click="openCreate('SIZE')">新增选项</PermissionButton>
    </div>
  
    <div class="option-grid">
      <section v-for="group in groups" :key="group.type" class="m-surface option-group">
        <div class="option-head">
          <SectionLabel :title="group.label" as="h3" />
          <PermissionButton perm="coffee:option:add" icon-only icon="plus" :label="`新增${group.label}`" size="sm" @click="openCreate(group.type)" />
        </div>
        <p v-if="!group.items.length" class="option-empty muted">暂无{{ group.label }}选项</p>
        <div v-for="item in group.items" :key="item.id" class="option-row">
          <div class="option-info">
            <span class="option-label">{{ item.label }}</span>
            <span class="mono muted">{{ Number(item.extraPrice) ? `+¥ ${item.extraPrice}` : '无加价' }} · 排序 {{ item.sortOrder }}</span>
          </div>
          <div class="option-ops">
            <Tag :tone="item.enabled ? 'success' : 'neutral'">{{ item.enabled ? '启用' : '停用' }}</Tag>
            <PermissionButton perm="coffee:option:edit" icon-only icon="edit" label="编辑" size="sm" @click="openEdit(item)" />
            <PermissionButton perm="coffee:option:remove" icon-only icon="trash" label="删除" danger size="sm" @click="remove(item)" />
          </div>
        </div>
      </section>
    </div>
  
    <FormDialog :open="dialog" :title="form.id === null ? '新增选项' : '编辑选项'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="类型" required>
        <Select v-model="form.type" :options="Object.keys(TYPE_LABEL).map(t => ({ label: TYPE_LABEL[t], value: t }))" :disabled="form.id !== null" />
      </Field>
      <Field label="名称" required :error="errors.label">
        <TextInput v-model="form.label" placeholder="燕麦奶" :invalid="!!errors.label" />
      </Field>
      <Field label="加价（元）" required :error="errors.extraPrice">
        <TextInput v-model="form.extraPrice" type="number" step="0.01" min="0" :invalid="!!errors.extraPrice" />
      </Field>
      <Field label="排序（小的在前）">
        <TextInput v-model="form.sortOrder" type="number" />
      </Field>
      <Field label="状态" group>
        <Switch v-model="form.enabled" :label="form.enabled ? '启用' : '停用'" />
      </Field>
    </FormDialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.option-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.option-group { padding: 18px; }
.option-head { display: flex; justify-content: space-between; align-items: center; }
.option-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-top: 1px solid var(--line, #e6dfd3); }
.option-info { display: flex; flex-direction: column; gap: 2px; }
.option-label { font-weight: 600; }
.option-ops { display: flex; align-items: center; gap: 6px; }
.option-empty { padding: 14px 0; font-size: 13px; }
</style>
