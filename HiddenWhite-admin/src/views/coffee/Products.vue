<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Field, Select, Switch, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ImageUpload, ListPage, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeImageSpec } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

interface ProductRow {
  id: string; name: string; subtitle: string; description: string; detailDescription: string
  price: number; image: string; detailImage: string; category: number; hasMilk: boolean; enabled: boolean
}

const CATEGORY_LABEL: Record<number, string> = { 0: '招牌咖啡', 1: '经典咖啡' }

const list = useListPage<ProductRow, { keyword: string; category: number | '' }>({
  query: { keyword: '', category: '' },
  fetch: params => coffeeApi.products.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'id', title: '商品 ID', width: 110 },
  { key: 'name', title: '商品名称', minWidth: 170 },
  { key: 'category', title: '分类', width: 100 },
  { key: 'price', title: '价格', width: 100 },
  { key: 'hasMilk', title: '含奶', width: 80 },
  { key: 'enabled', title: '状态', width: 90 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

interface ProductForm {
  id: string; name: string; subtitle: string; description: string; detailDescription: string
  price: number | null; image: string; detailImage: string; category: number; hasMilk: boolean; enabled: boolean
}
const emptyForm = (): ProductForm => ({
  id: '', name: '', subtitle: '', description: '', detailDescription: '',
  price: null, image: '', detailImage: '', category: 0, hasMilk: true, enabled: true,
})
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<ProductForm>(emptyForm())
const errors = ref<Partial<Record<keyof ProductForm, string>>>({})

/** 图片目标规格：列表图 / 详情图各自独立配置，由服务端下发 */
const imageSpec = ref<CoffeeImageSpec | null>(null)

onMounted(async () => {
  // 规格拉取失败不阻断表单，仅退化为组件内置的默认提示
  try {
    imageSpec.value = await coffeeApi.images.spec()
  } catch (error) {
    console.warn('图片规格获取失败，使用默认提示', error)
  }
})

function openCreate() {
  Object.assign(form, emptyForm())
  editingId.value = null
  errors.value = {}
  dialog.value = true
}

function openEdit(row: ProductRow) {
  Object.assign(form, { ...row })
  editingId.value = row.id
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    id: editingId.value ? [] : [required('请输入商品 ID，如 latte')],
    name: [required('请输入商品名称')],
    price: [required('请输入价格')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = {
    id: form.id.trim(), name: form.name.trim(), subtitle: form.subtitle.trim(),
    description: form.description.trim(), detailDescription: form.detailDescription.trim(),
    price: Number(form.price), image: form.image.trim(), detailImage: form.detailImage.trim(),
    category: form.category, hasMilk: form.hasMilk, enabled: form.enabled,
  }
  try {
    if (editingId.value) {
      await coffeeApi.products.update(editingId.value, payload)
      pushToast('商品已更新')
    } else {
      await coffeeApi.products.create(payload)
      pushToast('商品已新增')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '商品保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: ProductRow) {
  try {
    await coffeeApi.products.setEnabled(row.id, !row.enabled)
    await list.load()
  } catch (error) {
    toastError(error, '状态修改失败')
  }
}

async function remove(row: ProductRow) {
  const ok = await confirm({
    title: '删除商品',
    message: `确认删除商品「${row.name}」吗？已被订单引用的商品会自动转为下架。`,
    confirmText: '删除', danger: true,
  })
  if (!ok) return
  try {
    const result = await coffeeApi.products.remove(row.id)
    pushToast(result?.notice || '商品已删除')
    await list.load()
  } catch (error) {
    toastError(error, '商品删除失败')
  }
}
</script>

<template>
  <ListPage
    title="商品管理"
    desc="维护点单端（小程序 / H5）的商品列表与价格，改动实时生效。"
    table-title="商品列表"
    :list="list" :columns="columns" :min-width="880"
  >
    <template #actions>
      <PermissionButton perm="coffee:product:add" variant="primary" icon="plus" @click="openCreate">新增商品</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="商品 ID / 名称 / 副标题" clearable @enter="list.search()" />
      </Field>
      <Field label="分类">
        <Select v-model="list.query.category" :options="[{ label: '全部分类', value: '' }, { label: '招牌咖啡', value: 0 }, { label: '经典咖啡', value: 1 }]" @update:model-value="list.search()" />
      </Field>
    </template>

    <template #cell-id="{ row }"><span class="code-chip">{{ row.id }}</span></template>
    <template #cell-name="{ row }">
      <span class="cell-title">{{ row.name }}</span>
      <span class="muted" style="display: block; font-size: 12px">{{ row.subtitle }}</span>
    </template>
    <template #cell-category="{ row }">{{ CATEGORY_LABEL[row.category] ?? row.category }}</template>
    <template #cell-price="{ row }"><span class="mono">¥ {{ row.price }}</span></template>
    <template #cell-hasMilk="{ row }">{{ row.hasMilk ? '是' : '否' }}</template>
    <template #cell-enabled="{ row }">
      <Tag :tone="row.enabled ? 'success' : 'neutral'">{{ row.enabled ? '在售' : '下架' }}</Tag>
    </template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="coffee:product:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="coffee:product:edit" icon-only :icon="row.enabled ? 'pause' : 'play'" :label="row.enabled ? '下架' : '上架'" @click="toggleEnabled(row)" />
        <PermissionButton perm="coffee:product:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" :title="editingId ? '编辑商品' : '新增商品'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="商品 ID" required :error="errors.id" hint="英文标识，创建后不可修改，如 latte">
        <TextInput v-model="form.id" placeholder="latte" :invalid="!!errors.id" :disabled="!!editingId" />
      </Field>
      <Field label="商品名称" required :error="errors.name">
        <TextInput v-model="form.name" placeholder="招牌馥白" :invalid="!!errors.name" />
      </Field>
      <Field label="副标题">
        <TextInput v-model="form.subtitle" placeholder="双份浓缩 · 丝绒奶感" />
      </Field>
      <Field label="价格（元）" required :error="errors.price">
        <TextInput v-model="form.price" type="number" step="0.01" min="0" :invalid="!!errors.price" />
      </Field>
      <Field label="分类" required>
        <Select v-model="form.category" :options="[{ label: '招牌咖啡', value: 0 }, { label: '经典咖啡', value: 1 }]" />
      </Field>
      <Field label="是否含奶" group>
        <Switch v-model="form.hasMilk" :label="form.hasMilk ? '含奶（可选奶类）' : '无奶'" />
      </Field>
      <Field label="状态" group>
        <Switch v-model="form.enabled" :label="form.enabled ? '在售' : '下架'" />
      </Field>
      <Field label="列表图" span hint="用于点单端商品卡片，建议使用与商品主体一致的构图">
        <ImageUpload v-model="form.image" slot="list" :target="imageSpec?.list" :max-bytes="imageSpec?.maxBytes" />
      </Field>
      <Field label="详情图" span hint="用于点单端商品详情页大图">
        <ImageUpload v-model="form.detailImage" slot="detail" :target="imageSpec?.detail" :max-bytes="imageSpec?.maxBytes" />
      </Field>
      <Field label="简介（列表展示）" span>
        <TextInput v-model="form.description" multiline :rows="2" />
      </Field>
      <Field label="详情描述" span>
        <TextInput v-model="form.detailDescription" multiline :rows="2" />
      </Field>
    </FormDialog>
  </ListPage>
</template>
