<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Field, Select, Switch, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ImageUpload, ListPage, PermissionButton } from '@/components/business'
import { redeemApi, type CoffeeImageSpec, type RedeemProductRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { maxLength, required, validate } from '@/utils/validate'

/**
 * 兑换品管理。
 *
 * 与「商品管理」刻意分开：兑换品不走奶咖规格模型（无 SIZE/MILK/TEMPERATURE），
 * 价格单位是积分而不是元，库存可以为 NULL 表示不限量。把这些差异硬塞进商品表，
 * 会让两边都变得难以理解。
 */
const list = useListPage<RedeemProductRow, { keyword: string; enabled: '' | '1' | '0' }>({
  query: { keyword: '', enabled: '' },
  // 下拉选项的 value 只能是 string | number，这里就地转成布尔交给后端
  toParams: query => ({
    keyword: query.keyword,
    enabled: query.enabled === '' ? undefined : query.enabled === '1',
  }),
  fetch: params => redeemApi.products.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'id', title: '兑换品 ID', width: 130 },
  { key: 'name', title: '名称', minWidth: 180 },
  { key: 'pointsCost', title: '积分价', width: 100 },
  { key: 'stock', title: '库存', width: 100 },
  { key: 'perMemberLimit', title: '限兑', width: 90 },
  { key: 'sortOrder', title: '排序', width: 80 },
  { key: 'enabled', title: '状态', width: 90 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

interface RedeemForm {
  id: string
  name: string
  subtitle: string
  description: string
  image: string
  pointsCost: number | null
  /** 库存：null 代表「不限量」，0 代表「已兑完」，两者业务含义完全不同 */
  stock: number | null
  perMemberLimit: number | null
  sortOrder: number | null
  enabled: boolean
}

const emptyForm = (): RedeemForm => ({
  id: '', name: '', subtitle: '', description: '', image: '',
  pointsCost: null, stock: null, perMemberLimit: 0, sortOrder: 0, enabled: true,
})

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<RedeemForm>(emptyForm())
const errors = ref<Partial<Record<keyof RedeemForm, string>>>({})

/** 图片目标规格：与商品列表图同源（点单端卡片容器比例一致） */
const imageSpec = ref<CoffeeImageSpec | null>(null)

onMounted(async () => {
  try {
    imageSpec.value = await redeemApi.images.spec()
  } catch (error) {
    // 规格拉取失败不阻断表单，控件会退回内置默认提示
    console.warn('兑换品图片规格获取失败', error)
  }
})

function openCreate() {
  Object.assign(form, emptyForm())
  editingId.value = null
  errors.value = {}
  dialog.value = true
}

function openEdit(row: RedeemProductRow) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    subtitle: row.subtitle ?? '',
    description: row.description ?? '',
    image: row.image ?? '',
    pointsCost: row.pointsCost,
    stock: row.stock ?? null,
    perMemberLimit: row.perMemberLimit ?? 0,
    sortOrder: row.sortOrder ?? 0,
    enabled: row.enabled,
  })
  editingId.value = row.id
  errors.value = {}
  dialog.value = true
}

/** 库存 / 积分价 / 限兑的数值校验：required() 只判空，业务下限在这里把关 */
function checkNumbers() {
  const next: Partial<Record<keyof RedeemForm, string>> = {}
  const cost = Number(form.pointsCost)
  if (!Number.isFinite(cost) || cost < 1) next.pointsCost = '兑换所需积分必须大于 0'

  if (form.stock !== null) {
    if (!Number.isInteger(form.stock) || form.stock < 0) next.stock = '库存需为 0 或正整数；不限量请留空'
  }
  if (form.perMemberLimit !== null && form.perMemberLimit < 0) {
    next.perMemberLimit = '限兑次数不能为负数；不限请填 0'
  }
  errors.value = { ...errors.value, ...next }
  return Object.keys(next).length === 0
}

async function submit() {
  errors.value = validate(form, {
    id: editingId.value ? [] : [required('请输入兑换品 ID，如 rw-latte'), maxLength(40, 'ID 不能超过 40 个字符')],
    name: [required('请输入兑换品名称')],
    pointsCost: [required('请填写兑换所需积分')],
  })
  if (!checkNumbers()) return
  if (Object.keys(errors.value).length) return

  saving.value = true
  const payload = {
    id: form.id.trim(),
    name: form.name.trim(),
    subtitle: form.subtitle.trim(),
    description: form.description.trim(),
    image: form.image.trim(),
    pointsCost: Number(form.pointsCost),
    stock: form.stock,
    perMemberLimit: form.perMemberLimit ?? 0,
    sortOrder: form.sortOrder ?? 0,
    enabled: form.enabled,
  }
  try {
    if (editingId.value) {
      await redeemApi.products.update(editingId.value, payload)
      pushToast('兑换品已更新')
    } else {
      await redeemApi.products.create(payload)
      pushToast('兑换品已新增')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '兑换品保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: RedeemProductRow) {
  try {
    await redeemApi.products.setEnabled(row.id, !row.enabled)
    await list.load()
  } catch (error) {
    toastError(error, '状态修改失败')
  }
}

async function remove(row: RedeemProductRow) {
  const ok = await confirm({
    title: '删除兑换品',
    message: `确认删除「${row.name}」吗？已被兑换记录引用的兑换品不能删除，只能下架。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await redeemApi.products.remove(row.id)
    pushToast('兑换品已删除')
    await list.load()
  } catch (error) {
    toastError(error, '兑换品删除失败')
  }
}

const stockText = (row: RedeemProductRow) => (row.stock === null ? '不限' : String(row.stock))
const limitText = (row: RedeemProductRow) => (row.perMemberLimit > 0 ? `${row.perMemberLimit} 次` : '不限')
</script>

<template>
  <ListPage
    title="兑换品管理"
    desc="维护点单端积分商城的兑换品与积分价，改动实时生效；库存留空表示不限量。"
    table-title="兑换品列表"
    :list="list" :columns="columns" :min-width="900"
  >
    <template #actions>
      <PermissionButton perm="redeem:product:add" variant="primary" icon="plus" @click="openCreate">新增兑换品</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="兑换品 ID / 名称 / 副标题" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <Select
          v-model="list.query.enabled"
          :options="[{ label: '全部状态', value: '' }, { label: '已上架', value: '1' }, { label: '已下架', value: '0' }]"
          @update:model-value="list.search()"
        />
      </Field>
    </template>

    <template #cell-id="{ row }"><span class="code-chip">{{ row.id }}</span></template>
    <template #cell-name="{ row }">
      <span class="cell-title">{{ row.name }}</span>
      <span class="muted" style="display: block; font-size: 12px">{{ row.subtitle }}</span>
    </template>
    <template #cell-pointsCost="{ row }"><span class="mono">{{ row.pointsCost }} 分</span></template>
    <template #cell-stock="{ row }">
      <span :class="row.stock === null ? 'muted' : 'mono'">{{ stockText(row) }}</span>
    </template>
    <template #cell-perMemberLimit="{ row }"><span class="muted">{{ limitText(row) }}</span></template>
    <template #cell-sortOrder="{ row }"><span class="mono">{{ row.sortOrder }}</span></template>
    <template #cell-enabled="{ row }">
      <Tag :tone="row.enabled ? 'success' : 'neutral'">{{ row.enabled ? '已上架' : '已下架' }}</Tag>
    </template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="redeem:product:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton
          perm="redeem:product:edit" icon-only :icon="row.enabled ? 'pause' : 'play'"
          :label="row.enabled ? '下架' : '上架'" @click="toggleEnabled(row)"
        />
        <PermissionButton perm="redeem:product:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <FormDialog
      :open="dialog" :title="editingId ? '编辑兑换品' : '新增兑换品'" :saving="saving"
      @close="dialog = false" @submit="submit"
    >
      <Field label="兑换品 ID" required :error="errors.id" hint="英文标识，创建后不可修改，如 rw-latte">
        <TextInput v-model="form.id" placeholder="rw-latte" :invalid="!!errors.id" :disabled="!!editingId" />
      </Field>
      <Field label="名称" required :error="errors.name">
        <TextInput v-model="form.name" placeholder="经典拿铁" :invalid="!!errors.name" />
      </Field>
      <Field label="副标题">
        <TextInput v-model="form.subtitle" placeholder="到店核销 · 兑一杯" />
      </Field>
      <Field label="兑换所需积分" required :error="errors.pointsCost" hint="100 积分 ≈ 3 元等值，按此基准定价">
        <TextInput v-model="form.pointsCost" type="number" min="1" step="1" :invalid="!!errors.pointsCost" />
      </Field>
      <Field label="库存" :error="errors.stock" hint="留空表示不限量；填 0 表示已兑完">
        <TextInput v-model="form.stock" type="number" min="0" step="1" placeholder="留空 = 不限量" :invalid="!!errors.stock" />
      </Field>
      <Field label="每人限兑次数" :error="errors.perMemberLimit" hint="0 表示不限；待核销与已核销都计入">
        <TextInput v-model="form.perMemberLimit" type="number" min="0" step="1" :invalid="!!errors.perMemberLimit" />
      </Field>
      <Field label="排序" hint="数字越小越靠前">
        <TextInput v-model="form.sortOrder" type="number" step="1" />
      </Field>
      <Field label="状态" group>
        <Switch v-model="form.enabled" :label="form.enabled ? '已上架' : '已下架'" />
      </Field>
      <Field label="兑换品图片" span hint="用于点单端兑换卡片，与商品列表图同一比例">
        <ImageUpload
          v-model="form.image"
          slot="list"
          :target="imageSpec?.list"
          :max-bytes="imageSpec?.maxBytes"
          :uploader="redeemApi.images.upload"
        />
      </Field>
      <Field label="描述" span hint="展示在兑换确认弹层，说明兑换规则或口味">
        <TextInput v-model="form.description" multiline :rows="2" />
      </Field>
    </FormDialog>
  </ListPage>
</template>
