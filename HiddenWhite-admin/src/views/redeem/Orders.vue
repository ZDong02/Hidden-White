<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@/components/business'
import { redeemApi, type RedemptionRow, type RedemptionStatus } from '@/api'
import { useListPage } from '@/composables/list'
import { pushToast, toastError } from '@/composables/toast'

/**
 * 兑换记录 + 到店核销。
 *
 * 核销的入口有两个，都走同一个弹层：
 *   1. 页头「核销兑换码」——顾客报号码，店员手输（主路径，号码只在顾客手机上）；
 *   2. 行内「核销」——已在列表里定位到某一单时免去手输（如顾客报单号由客服查到）。
 * 两条路径最终都调用 POST /redeem/orders/verify，服务端按核销码做条件更新。
 */
const STATUS: Record<string, { label: string; tone: 'warning' | 'success' | 'neutral' }> = {
  PENDING: { label: '待核销', tone: 'warning' },
  REDEEMED: { label: '已核销', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  EXPIRED: { label: '已过期', tone: 'neutral' },
}

const STATUS_FILTER = [
  { label: '全部状态', value: '' },
  { label: '待核销', value: 'PENDING' },
  { label: '已核销', value: 'REDEEMED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已过期', value: 'EXPIRED' },
]

const list = useListPage<RedemptionRow, { keyword: string; status: string }>({
  query: { keyword: '', status: '' },
  fetch: params => redeemApi.orders.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'redeemNo', title: '兑换单号', width: 140 },
  { key: 'productName', title: '兑换品', minWidth: 160 },
  { key: 'memberName', title: '会员', width: 130 },
  { key: 'code', title: '核销码', width: 110 },
  { key: 'pointsCost', title: '扣分', width: 90 },
  { key: 'status', title: '状态', width: 100 },
  { key: 'expiresAt', title: '有效期至', width: 160 },
  { key: 'createdAt', title: '兑换时间', width: 160 },
  { key: 'actions', title: '操作', width: 80, className: 'col-actions' },
]

const verifyOpen = ref(false)
const verifying = ref(false)
const codeForm = reactive({ code: '' })
const codeError = ref('')

function openVerify(code = '') {
  codeForm.code = code
  codeError.value = ''
  verifyOpen.value = true
}

async function submitVerify() {
  const code = codeForm.code.trim()
  // 只做格式前置校验；「是否存在 / 是否过期 / 是否已用」一律由服务端判定
  if (!/^\d{8}$/.test(code)) {
    codeError.value = '请输入 8 位数字核销码'
    return
  }
  verifying.value = true
  try {
    const result = await redeemApi.orders.verify(code)
    pushToast(`已核销「${result.productName}」· 扣 ${result.pointsCost} 积分`)
    verifyOpen.value = false
    codeForm.code = ''
    await list.load()
  } catch (error) {
    toastError(error, '核销失败')
  } finally {
    verifying.value = false
  }
}

/** 状态下方补充「谁在什么时候核销 / 为什么失效」，避免运营再去别处查 */
function statusNote(row: RedemptionRow) {
  if (row.status === 'REDEEMED') {
    return `${row.verifiedBy || '店员'} · ${row.verifiedAt || ''}`
  }
  if (row.status === 'CANCELLED') return '会员自助取消，积分已退回'
  if (row.status === 'EXPIRED') return '逾期未核销，积分已退回'
  return ''
}
</script>

<template>
  <ListPage
    title="兑换记录"
    desc="查看积分兑换单与核销状态；顾客到店报出 8 位核销码即可核销，逾期未核销的积分由系统自动退回。"
    table-title="兑换单列表"
    :list="list" :columns="columns" :min-width="1080"
  >
    <template #actions>
      <PermissionButton perm="redeem:order:verify" variant="primary" icon="check" @click="openVerify()">核销兑换码</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="兑换单号 / 核销码 / 兑换品 / 会员" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <Select v-model="list.query.status" :options="STATUS_FILTER" @update:model-value="list.search()" />
      </Field>
    </template>

    <template #cell-redeemNo="{ row }"><span class="code-chip">{{ row.redeemNo }}</span></template>
    <template #cell-productName="{ row }">
      <span class="cell-title">{{ row.productName }}</span>
      <span class="muted" style="display: block; font-size: 12px">{{ row.productId }}</span>
    </template>
    <template #cell-memberName="{ row }">
      {{ row.memberName || '—' }}
      <span class="muted" style="display: block; font-size: 12px">{{ row.memberId }}</span>
    </template>
    <template #cell-code="{ row }"><span class="code-chip mono">{{ row.code }}</span></template>
    <template #cell-pointsCost="{ row }"><span class="mono">{{ row.pointsCost }} 分</span></template>
    <template #cell-status="{ row }">
      <Tag :tone="STATUS[row.status]?.tone ?? 'neutral'">{{ STATUS[row.status]?.label ?? row.status }}</Tag>
      <span v-if="statusNote(row)" class="muted" style="display: block; font-size: 12px">{{ statusNote(row) }}</span>
    </template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <!-- 只有待核销才给核销入口：已核销/已取消/已过期的码再提交必然失败 -->
        <PermissionButton
          v-if="row.status === 'PENDING'"
          perm="redeem:order:verify" icon-only icon="check" label="核销"
          @click="openVerify(row.code)"
        />
      </div>
    </template>

    <FormDialog
      :open="verifyOpen" title="核销兑换码" sub="请与顾客核对兑换品与核销码后确认"
      :saving="verifying" submit-text="确认核销"
      @close="verifyOpen = false" @submit="submitVerify"
    >
      <Field label="核销码" required span :error="codeError" hint="顾客在「积分兑换 → 我的兑换 → 核销码」中出示">
        <TextInput
          v-model="codeForm.code"
          placeholder="8 位数字"
          :maxlength="8"
          :invalid="!!codeError"
          @enter="submitVerify"
        />
      </Field>
    </FormDialog>
  </ListPage>
</template>

<style scoped>
.code-chip {
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  letter-spacing: 0.4px;
}
</style>
