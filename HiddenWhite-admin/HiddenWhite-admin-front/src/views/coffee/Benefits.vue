<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Field, Switch, Tag, TextInput } from '@/components/ui'
import { FormDialog, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeBenefitRow } from '@/api'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

/* 会员权益是展示卡片集合，数量少，直接全量加载 */
const benefits = ref<CoffeeBenefitRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    benefits.value = await coffeeApi.benefits.list()
  } catch (error) {
    toastError(error, '权益加载失败')
  } finally {
    loading.value = false
  }
}

interface BenefitForm { id: number | null; icon: string; title: string; description: string; sortOrder: number; enabled: boolean }
const dialog = ref(false)
const saving = ref(false)
const form = reactive<BenefitForm>({ id: null, icon: '◇', title: '', description: '', sortOrder: 0, enabled: true })
const errors = ref<Partial<Record<keyof BenefitForm, string>>>({})

function openCreate() {
  Object.assign(form, { id: null, icon: '◇', title: '', description: '', sortOrder: benefits.value.length + 1, enabled: true })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: CoffeeBenefitRow) {
  Object.assign(form, { ...row })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    icon: [required('请输入图标字符')],
    title: [required('请输入权益标题')],
    description: [required('请输入权益描述')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = { icon: form.icon.trim(), title: form.title.trim(), description: form.description.trim(), sortOrder: form.sortOrder, enabled: form.enabled }
  try {
    if (form.id === null) {
      await coffeeApi.benefits.create(payload)
      pushToast('权益已新增')
    } else {
      await coffeeApi.benefits.update(form.id, payload)
      pushToast('权益已更新')
    }
    dialog.value = false
    await load()
  } catch (error) {
    toastError(error, '权益保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CoffeeBenefitRow) {
  const ok = await confirm({ title: '删除权益', message: `确认删除权益「${row.title}」吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await coffeeApi.benefits.remove(row.id)
    pushToast('权益已删除')
    await load()
  } catch (error) {
    toastError(error, '权益删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">会员权益</h2>
        <p class="page-desc">点单端会员页展示的权益卡片，按排序号从小到大排列。</p>
      </div>
      <PermissionButton perm="coffee:benefit:add" variant="primary" icon="plus" @click="openCreate">新增权益</PermissionButton>
    </div>
  
    <div class="benefit-grid">
      <div v-for="row in benefits" :key="row.id" :class="['m-surface', 'benefit-card', { 'is-off': !row.enabled }]">
        <span class="benefit-icon">{{ row.icon }}</span>
        <h3 class="benefit-title">{{ row.title }}</h3>
        <p class="benefit-desc">{{ row.description }}</p>
        <div class="benefit-foot">
          <span class="mono muted">排序 {{ row.sortOrder }}</span>
          <Tag :tone="row.enabled ? 'success' : 'neutral'">{{ row.enabled ? '展示中' : '已隐藏' }}</Tag>
          <div class="benefit-ops">
            <PermissionButton perm="coffee:benefit:edit" icon-only icon="edit" label="编辑" size="sm" @click="openEdit(row)" />
            <PermissionButton perm="coffee:benefit:remove" icon-only icon="trash" label="删除" danger size="sm" @click="remove(row)" />
          </div>
        </div>
      </div>
    </div>
  
    <FormDialog :open="dialog" :title="form.id === null ? '新增权益' : '编辑权益'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="图标" required :error="errors.icon" hint="单个装饰字符，如 ◇ ✧ ％ ＋ ☆">
        <TextInput v-model="form.icon" :maxlength="4" :invalid="!!errors.icon" />
      </Field>
      <Field label="标题" required :error="errors.title">
        <TextInput v-model="form.title" placeholder="积分兑换" :invalid="!!errors.title" />
      </Field>
      <Field label="描述" required :error="errors.description">
        <TextInput v-model="form.description" placeholder="好礼随心兑" :invalid="!!errors.description" />
      </Field>
      <Field label="排序（小的在前）">
        <TextInput v-model="form.sortOrder" type="number" />
      </Field>
      <Field label="状态" group>
        <Switch v-model="form.enabled" :label="form.enabled ? '展示中' : '已隐藏'" />
      </Field>
    </FormDialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.page-title { font-size: 22px; }
.page-desc { color: var(--ink-soft, #6f675c); font-size: 13px; margin-top: 6px; }
.benefit-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.benefit-card { padding: 20px; text-align: center; transition: opacity 0.2s ease; }
.benefit-card.is-off { opacity: 0.55; }
.benefit-icon { font-size: 30px; color: var(--accent, #a8834f); }
.benefit-title { margin-top: 8px; font-size: 15px; }
.benefit-desc { margin: 6px 0 12px; font-size: 12px; color: var(--ink-soft, #6f675c); }
.benefit-foot { display: flex; align-items: center; justify-content: center; gap: 8px; }
.benefit-ops { display: flex; gap: 4px; }
</style>
