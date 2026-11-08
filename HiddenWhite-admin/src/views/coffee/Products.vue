<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Field, Select, Switch, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ImageUpload, ListPage, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeCategoryRow, type CoffeeImageSpec } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

interface ProductRow {
  id: string; name: string; subtitle: string; description: string; detailDescription: string
  price: number; image: string; detailImage: string; category: number; hasMilk: boolean; enabled: boolean
}

/**
 * 分类字典由后端下发（分类管理页维护），这里不再写死 0/1。
 * 拉取失败时退回这份兜底，只是为了「页面还能用」，不是第二份真源：
 * 分类一旦在后台增删，兜底就会与事实不符，所以只当异常路径。
 */
const FALLBACK_CATEGORY_LABEL: Record<number, string> = { 0: '招牌精选', 1: '意式咖啡' }
const categories = ref<CoffeeCategoryRow[]>([])

const categoryOptions = computed(() => categories.value.map(item => ({ label: item.name, value: item.code })))
const categoryLabel = computed(() => new Map(categories.value.map(item => [item.code, item.name])))

/** 显示用：后端字典 → 兜底 → 原始数字（分类被删或接口失败时不至于显示空白） */
function categoryName(code: number) {
  return categoryLabel.value.get(code) ?? FALLBACK_CATEGORY_LABEL[code] ?? code
}

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
  // 分类与图片规格并发拉取，任一失败都不阻断列表：分类有兜底名称，规格有内置提示
  const [categoryResult, specResult] = await Promise.allSettled([
    coffeeApi.categories.list(),
    coffeeApi.images.spec(),
  ])

  if (categoryResult.status === 'fulfilled') {
    categories.value = categoryResult.value
  } else {
    console.warn('分类字典获取失败，使用兜底名称', categoryResult.reason)
  }

  if (specResult.status === 'fulfilled') {
    imageSpec.value = specResult.value
  } else {
    console.warn('图片规格获取失败，使用默认提示', specResult.reason)
  }
})

function openCreate() {
  Object.assign(form, emptyForm())
  // 新增时默认落在第一个分类上：分类已经不由前端写死，0 未必存在
  form.category = categories.value[0]?.code ?? 0
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
        <Select v-model="list.query.category" :options="[{ label: '全部分类', value: '' }, ...categoryOptions]" @update:model-value="list.search()" />
      </Field>
    </template>

    <template #cell-id="{ row }"><span class="code-chip">{{ row.id }}</span></template>
    <template #cell-name="{ row }">
      <span class="cell-title">{{ row.name }}</span>
      <span class="muted" style="display: block; font-size: 12px">{{ row.subtitle }}</span>
    </template>
    <template #cell-category="{ row }">{{ categoryName(row.category) }}</template>
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
      <Field label="分类" required hint="分类在「分类管理」里维护，新增后这里即时可选">
        <Select v-model="form.category" :options="categoryOptions" />
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
