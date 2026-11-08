<script setup lang="ts">
import { reactive, ref } from 'vue'
import { DataTable, Field, IconButton, Panel, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeCategoryRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

/**
 * 分类管理：商品分类的增、删、查。
 *
 * 版式走 ListPage 的 altView 分支 —— 页头、右上角「新增分类」、面板标题与刷新按钮
 * 与商品管理完全一致，只是不渲染查询区与分页：分类是个位数，
 * 给两行数据配翻页器会让人以为页面没加载完。
 */
const list = useListPage<CoffeeCategoryRow, Record<string, never>>({
  query: {},
  fetch: async () => {
    const rows = await coffeeApi.categories.list()
    // 全量返回，包成一次「单页」结果复用 ListPage 的加载态与空态处理
    return { rows, total: rows.length, pageNum: 1, pageSize: rows.length || 1 }
  },
})

const columns: Column[] = [
  { key: 'code', title: '分类编码', width: 100 },
  { key: 'name', title: '分类名称', minWidth: 200 },
  { key: 'productCount', title: '商品数', width: 110 },
  { key: 'sortOrder', title: '排序', width: 90 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

interface CategoryForm { name: string; sortOrder: number | null }
const emptyForm = (): CategoryForm => ({ name: '', sortOrder: null })

const dialog = ref(false)
const saving = ref(false)
const form = reactive<CategoryForm>(emptyForm())
const errors = ref<Partial<Record<keyof CategoryForm, string>>>({})

function openCreate() {
  Object.assign(form, emptyForm())
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    name: [required('请输入分类名称')],
  })
  if (Object.keys(errors.value).length) return

  saving.value = true
  try {
    await coffeeApi.categories.create({
      name: form.name.trim(),
      // 留空交给后端按编码递增分配，避免运营为了排序去想「填几」
      sortOrder: form.sortOrder === null || form.sortOrder === undefined ? undefined : Number(form.sortOrder),
    })
    pushToast('分类已创建')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '分类保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CoffeeCategoryRow) {
  // 先按商品数拦一道：比直接调接口再弹 409 更早、也更明确地告诉运营要先去改哪
  if (row.productCount > 0) {
    await confirm({
      title: '无法删除',
      message: `“${row.name}”下还有 ${row.productCount} 款商品，请先把商品改到其他分类再删除。`,
      confirmText: '知道了',
    })
    return
  }

  const ok = await confirm({
    title: '删除分类',
    message: `确认删除分类“${row.name}”吗？删除后它不再出现在商品筛选和点单端分类栏。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await coffeeApi.categories.remove(row.code)
    pushToast('分类已删除')
    await list.load()
  } catch (error) {
    toastError(error, '分类删除失败')
  }
}
</script>

<template>
  <ListPage
    title="分类管理"
    desc="维护点单端分类栏与商品筛选用的分类。分类编码由系统分配，商品按编码归属，删除前需先清空该分类下的商品。"
    :list="list"
    :columns="columns"
    alt-view
  >
    <template #actions>
      <PermissionButton perm="coffee:category:add" variant="primary" icon="plus" @click="openCreate">
        新增分类
      </PermissionButton>
    </template>

    <template #alt>
      <Panel title="分类列表" :meta="`共 ${list.total.value} 条`">
        <template #tools>
          <IconButton name="refresh" label="刷新" bordered @click="list.load()" />
        </template>

        <DataTable
          :columns="columns"
          :data="list.rows.value"
          :loading="list.loading.value"
          row-key="code"
          empty-text="还没有分类，点右上角「新增分类」建第一个"
        >
          <template #cell-code="{ row }">
            <span class="code-chip">{{ row.code }}</span>
          </template>
          <template #cell-name="{ row }">
            <span class="cell-title">{{ row.name }}</span>
          </template>
          <template #cell-productCount="{ row }">
            <Tag :tone="row.productCount ? 'neutral' : 'success'">
              {{ row.productCount ? `${row.productCount} 款` : '空分类' }}
            </Tag>
          </template>
          <template #cell-actions="{ row }">
            <div class="row-actions">
              <PermissionButton
                perm="coffee:category:remove"
                icon-only
                icon="trash"
                label="删除"
                danger
                @click="remove(row)"
              />
            </div>
          </template>
        </DataTable>
      </Panel>
    </template>

    <FormDialog
      :open="dialog"
      title="新增分类"
      :saving="saving"
      @close="dialog = false"
      @submit="submit"
    >
      <Field label="分类名称" required :error="errors.name" hint="显示在点单端分类栏与商品筛选，最多 20 个字">
        <TextInput v-model="form.name" placeholder="茶饮" :invalid="!!errors.name" />
      </Field>
      <Field label="排序" hint="小的排在前面，留空按创建顺序">
        <TextInput v-model="form.sortOrder" type="number" placeholder="留空自动分配" />
      </Field>
    </FormDialog>
  </ListPage>
</template>
