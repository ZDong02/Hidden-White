<script setup lang="ts">
import { ref } from 'vue'
import { Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeOrderRow } from '@/api'
import { useListPage } from '@/composables/list'
import { useAutoRefresh } from '@/composables/poll'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'

/** 订单状态映射，与后端 OrderStatus 五态严格对应。 */
const STATUS: Record<string, { label: string; tone: 'warning' | 'success' | 'info' | 'neutral' }> = {
  CREATED: { label: '待支付', tone: 'neutral' },
  PENDING_PAYMENT: { label: '待支付', tone: 'warning' },
  PAID: { label: '制作中', tone: 'info' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
}

/** 取消原因说明。区分「钱没到账」与「钱到了但顾客没来」，后者需人工退款。 */
const CANCEL_REASON: Record<string, string> = {
  TIMEOUT_UNPAID: '超时未支付，自动取消',
  TIMEOUT_UNPICKED: '超时未取餐（已支付，需退款）',
  MANUAL: '人工取消',
  PAY_FAILED: '支付失败，自动取消',
}

const STATUS_FILTER = [
  { label: '全部状态', value: '' },
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '制作中', value: 'PAID' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]

const list = useListPage<CoffeeOrderRow, { keyword: string; status: string }>({
  query: { keyword: '', status: '' },
  fetch: params => coffeeApi.orders.list(params) as Promise<any>,
})

// 顾客下单直写共享库，每 10 秒静默拉取新订单（标签页隐藏时暂停），无需手动刷新
useAutoRefresh(() => list.load({ silent: true }), 10_000)

const columns: Column[] = [
  { key: 'pickup', title: '取餐号', width: 90 },
  { key: 'orderNumber', title: '订单号', width: 130 },
  { key: 'memberId', title: '会员', minWidth: 120 },
  { key: 'items', title: '商品明细', minWidth: 220 },
  { key: 'total', title: '实付', width: 100 },
  { key: 'status', title: '状态', width: 90 },
  { key: 'createdAt', title: '下单时间', width: 160 },
  { key: 'actions', title: '操作', width: 90, className: 'col-actions' },
]

const completing = ref<string | null>(null)

async function complete(row: CoffeeOrderRow) {
  completing.value = row.id
  try {
    await coffeeApi.orders.complete(row.id)
    pushToast(`订单 ${row.orderNumber} 已完成`)
    await list.load()
  } catch (error) {
    toastError(error, '订单完成失败')
  } finally {
    completing.value = null
  }
}

async function remove(row: CoffeeOrderRow) {
  const ok = await confirm({
    title: '删除订单',
    message: `确认删除订单 ${row.orderNumber}（取餐号 ${row.pickup}）吗？该操作不可恢复。`,
    confirmText: '删除', danger: true,
  })
  if (!ok) return
  try {
    await coffeeApi.orders.remove(row.id)
    pushToast('订单已删除')
    await list.load()
  } catch (error) {
    toastError(error, '订单删除失败')
  }
}

const itemsText = (row: CoffeeOrderRow) =>
  (row.items ?? []).map(item => `${item.name} ×${item.quantity}`).join('、') || '—'
</script>

<template>
  <ListPage
    title="订单管理"
    desc="查看点单端全部订单与制作状态，可标记完成或删除；会员在点单端取餐时以取餐号为准。新订单每 10 秒自动刷新。"
    table-title="订单列表"
    :list="list" :columns="columns" :min-width="1000"
  >
    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="订单号 / 取餐号 / 会员" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <Select
          v-model="list.query.status"
          :options="STATUS_FILTER"
          @update:model-value="list.search()"
        />
      </Field>
    </template>

    <template #cell-pickup="{ row }"><span class="pickup">{{ row.pickup }}</span></template>
    <template #cell-orderNumber="{ row }"><span class="code-chip">{{ row.orderNumber }}</span></template>
    <template #cell-items="{ row }"><span class="muted">{{ itemsText(row) }}</span></template>
    <template #cell-total="{ row }">
      <span class="mono">¥ {{ row.total }}</span>
      <span v-if="Number(row.discount)" class="mono muted" style="display: block; font-size: 12px">已减 ¥ {{ row.discount }}</span>
    </template>
    <template #cell-status="{ row }">
      <Tag :tone="STATUS[row.status]?.tone ?? 'neutral'">{{ STATUS[row.status]?.label ?? row.status }}</Tag>
      <span v-if="row.cancelReason" class="muted" style="display: block; font-size: 12px">
        {{ CANCEL_REASON[row.cancelReason] ?? row.cancelReason }}
      </span>
    </template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <!-- 只有已支付（PAID）才能标记完成：未支付直接放行会造成漏收 -->
        <PermissionButton
          v-if="row.status === 'PAID'"
          perm="coffee:order:edit" icon-only icon="check" label="完成取餐"
          :loading="completing === row.id" @click="complete(row)"
        />
        <PermissionButton perm="coffee:order:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>
  </ListPage>
</template>

<style scoped>
.pickup { font-family: Georgia, serif; font-size: 16px; font-weight: 600; color: var(--accent, #a8834f); }
</style>
