<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Field, Select, Switch, Tag, TextInput, type Column } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import { couponApi, type CouponConfig, type CouponRecordRow, type CouponWeekOption } from '@/api'
import { useListPage } from '@/composables/list'
import { hasPermission } from '@/directives/permission'
import { pushToast, toastError } from '@/composables/toast'

/**
 * 每周优惠券运营页。
 *
 * 左栏是全局配置（面额 / 门槛 / 有效期 / 发行量 / 文案），右侧是发放与使用记录。
 *
 * <p>三条必须让运营看见的事实，所以直接写在页面说明里而不是藏进帮助文档：
 * ① 领取周期是自然周（周一 00:00 重置），每人每周一张；
 * ② 面额与门槛是<b>领取时的快照</b>，改配置只影响之后发出的券；
 * ③ 券与首单礼遇互斥，系统自动取优惠更大的一边。
 *
 * <p>所有数字改一行就影响全体会员，因此前端只做「明显填错」的第一道拦截，
 * 真正的规则（面额上限、门槛不低于面额、发行量不得低于已发出数）以后端为准。
 */
const config = ref<CouponConfig | null>(null)
const weeks = ref<CouponWeekOption[]>([])

const form = reactive({
  enabled: true,
  amount: 6,
  minSpend: 20,
  validDays: 7,
  maxHeld: 2,
  /** 空串表示不限量；接口用 null 表达同一件事 */
  totalStock: '' as string | number,
  title: '每周好券',
  subtitle: '满 20 元减 6 元',
  description: '',
})
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const canEdit = computed(() => hasPermission('coffee:coupon:edit'))

/** 不限量时用 0 占位展示「已发 N 张」，避免出现「剩余 null」这种字样 */
const stockLabel = computed(() => {
  const data = config.value
  if (!data) return '—'
  if (data.totalStock == null) return `已发 ${data.issuedCount} 张 · 不限量`
  return `已发 ${data.issuedCount} / ${data.totalStock} 张`
})

async function loadConfig() {
  try {
    const data = await couponApi.config()
    config.value = data
    Object.assign(form, {
      enabled: data.enabled,
      amount: data.amount,
      minSpend: data.minSpend,
      validDays: data.validDays,
      maxHeld: data.maxHeld,
      totalStock: data.totalStock == null ? '' : data.totalStock,
      title: data.title,
      subtitle: data.subtitle,
      description: data.description,
    })
  } catch (error) {
    toastError(error, '优惠券配置加载失败')
  }
}

async function loadWeeks() {
  try {
    weeks.value = await couponApi.weeks()
  } catch {
    // 周下拉只是筛选便利，取不到就退化成「全部周」
    weeks.value = []
  }
}

async function submit() {
  errors.value = {}
  if (!Number.isFinite(form.amount) || form.amount <= 0) {
    errors.value.amount = '券面额必须大于 0'
  } else if (form.amount > 200) {
    errors.value.amount = '券面额不能超过 200 元'
  }
  if (!Number.isFinite(form.minSpend) || form.minSpend < 0) {
    errors.value.minSpend = '使用门槛不能为负数'
  } else if (form.minSpend > 0 && form.minSpend < form.amount) {
    // 门槛低于面额等于无门槛，也意味着订单可能被减到 0
    errors.value.minSpend = '门槛不能低于券面额，否则等于无门槛'
  }
  if (!Number.isFinite(form.validDays) || form.validDays < 1 || form.validDays > 90) {
    errors.value.validDays = '有效期需在 1–90 天之间'
  }
  if (!Number.isFinite(form.maxHeld) || form.maxHeld < 0 || form.maxHeld > 10) {
    errors.value.maxHeld = '持有上限需在 0–10 张之间（0 表示不限）'
  }
  const issued = config.value?.issuedCount ?? 0
  if (form.totalStock !== '' && (!Number.isFinite(Number(form.totalStock)) || Number(form.totalStock) < issued)) {
    errors.value.totalStock = `发行量不能小于已发出的 ${issued} 张；如需停发请直接关闭领取`
  }
  if (!form.title.trim()) errors.value.title = '请输入券标题'
  if (Object.keys(errors.value).length) return

  saving.value = true
  try {
    await couponApi.saveConfig({
      enabled: form.enabled,
      amount: Number(form.amount),
      minSpend: Number(form.minSpend),
      validDays: Number(form.validDays),
      maxHeld: Number(form.maxHeld),
      totalStock: form.totalStock === '' ? null : Number(form.totalStock),
      title: form.title.trim(),
      subtitle: form.subtitle.trim(),
      description: form.description.trim(),
    })
    pushToast('优惠券配置已保存')
    await loadConfig()
  } catch (error) {
    toastError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------------- 发放与使用记录 ---------------- */

const list = useListPage<CouponRecordRow, { status: string; weekKey: string; keyword: string }>({
  query: { status: '', weekKey: '', keyword: '' },
  fetch: params => couponApi.records(params) as Promise<any>,
  pageSize: 10,
})

const columns: Column[] = [
  { key: 'displayName', title: '会员', minWidth: 140 },
  { key: 'phone', title: '手机号', width: 120 },
  { key: 'weekKey', title: '归属周', width: 110 },
  { key: 'amount', title: '面额', width: 90 },
  { key: 'status', title: '状态', width: 90 },
  { key: 'claimedAt', title: '领取时间', width: 140 },
  { key: 'usedAt', title: '使用时间', width: 140 },
  { key: 'orderNumber', title: '核销订单', width: 140 },
  { key: 'discountAmount', title: '抵扣', width: 90 },
]

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待使用', value: 'UNUSED' },
  { label: '已使用', value: 'USED' },
  { label: '已过期', value: 'EXPIRED' },
]

const weekOptions = computed(() => [
  { label: '全部周', value: '' },
  ...weeks.value.map(week => ({ label: week.label, value: week.key })),
])

function statusTone(status: string) {
  if (status === 'UNUSED') return 'ok'
  if (status === 'USED') return 'info'
  return 'neutral'
}

function statusText(status: string) {
  if (status === 'UNUSED') return '待使用'
  if (status === 'USED') return '已使用'
  return '已过期'
}

function money(value: number | null | undefined) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `¥${Number(num.toFixed(2))}`
}

onMounted(async () => {
  await Promise.all([loadConfig(), loadWeeks()])
  await list.load()
})
</script>

<template>
  <ListPage
    title="优惠券"
    desc="每位会员每自然周可领一张满减券（周一 00:00 重置）。面额与门槛改完立即对之后发出的券生效，已发出去的券保持领取时的面额。"
    table-title="发放与使用记录"
    :list="list"
    :columns="columns"
    :min-width="1040"
  >
    <template #side>
      <section class="cp-config">
        <h3 class="cp-config-title">券配置</h3>
        <p class="cp-config-note">
          每周一张，自然周重置；与首单礼遇互斥，系统取优惠更大的一边。面额与门槛按领取时快照，改配置不影响已发出的券。
        </p>

        <dl class="cp-stats">
          <div class="cp-stat">
            <dt>本周已领</dt>
            <dd class="mono">{{ config?.claimedThisWeek ?? 0 }}</dd>
          </div>
          <div class="cp-stat">
            <dt>本周核销</dt>
            <dd class="mono">{{ config?.usedThisWeek ?? 0 }}</dd>
          </div>
          <div class="cp-stat">
            <dt>当前未使用</dt>
            <dd class="mono">{{ config?.activeCount ?? 0 }}</dd>
          </div>
          <div class="cp-stat">
            <dt>累计抵扣</dt>
            <dd class="mono">{{ money(config?.discountTotal) }}</dd>
          </div>
        </dl>

        <!-- 券面预览：尺寸按点单端 1:1 折算（rpx ÷ 2），改面额门槛时所见即所得 -->
        <div class="cp-preview">
          <div class="cp-preview-top">
            <span class="cp-preview-kicker">WEEKLY COUPON</span>
            <span class="cp-preview-badge">{{ form.enabled ? '本周可领取' : '暂未开放' }}</span>
          </div>
          <div class="cp-preview-face">
            <span class="cp-preview-amount mono"><i>¥</i>{{ Number(form.amount) || 0 }}</span>
            <span class="cp-preview-meta">
              满 {{ Number(form.minSpend) || 0 }} 元可用
              <em>领取后 {{ Number(form.validDays) || 0 }} 天内有效</em>
            </span>
          </div>
        </div>
        <p class="cp-preview-caption">
          点单端券包首屏预览（1:1，尺寸 = rpx ÷ 2）
        </p>

        <Field label="是否开放">
          <Switch v-model="form.enabled" :label="form.enabled ? '点单端可领取' : '已关闭，券包显示暂未开放'" />
        </Field>

        <Field label="券面额（元）" required :error="errors.amount" hint="建议不超过客单价的三分之一">
          <TextInput v-model="form.amount" type="number" :invalid="!!errors.amount" />
        </Field>

        <Field
          label="使用门槛（元）"
          :error="errors.minSpend"
          hint="按订单商品金额判定，不含包装费；填 0 表示无门槛"
        >
          <TextInput v-model="form.minSpend" type="number" :invalid="!!errors.minSpend" />
        </Field>

        <Field label="有效期（天）" :error="errors.validDays" hint="从领取时刻起算，精确到分钟">
          <TextInput v-model="form.validDays" type="number" :invalid="!!errors.validDays" />
        </Field>

        <Field
          label="持有上限（张）"
          :error="errors.maxHeld"
          hint="未使用张数上限，防止长期不用的人囤券；0 表示不限"
        >
          <TextInput v-model="form.maxHeld" type="number" :invalid="!!errors.maxHeld" />
        </Field>

        <Field
          label="总发行量"
          :error="errors.totalStock"
          :hint="`留空表示不限量。当前${stockLabel}`"
        >
          <TextInput v-model="form.totalStock" type="number" :invalid="!!errors.totalStock" placeholder="留空 = 不限量" />
        </Field>

        <Field label="券标题" required :error="errors.title" hint="点单端券面与列表共用">
          <TextInput v-model="form.title" :maxlength="40" :invalid="!!errors.title" />
        </Field>

        <Field label="券副标题" hint="领取说明的第二行，建议 12 个字以内">
          <TextInput v-model="form.subtitle" :maxlength="60" />
        </Field>

        <Field label="使用说明" hint="展示在点单端券面下方，写清限制条件">
          <TextInput v-model="form.description" :maxlength="200" />
        </Field>

        <PermissionButton
          perm="coffee:coupon:edit"
          variant="primary"
          :disabled="saving || !canEdit"
          style="width: 100%; margin-top: 8px;"
          @click="submit"
        >
          {{ saving ? '保存中…' : '保存配置' }}
        </PermissionButton>
      </section>
    </template>

    <template #search>
      <Field label="状态">
        <Select v-model="list.query.status" :options="statusOptions" />
      </Field>
      <Field label="归属周">
        <Select v-model="list.query.weekKey" :options="weekOptions" />
      </Field>
      <Field label="关键词" wide>
        <TextInput
          v-model="list.query.keyword"
          icon="search"
          placeholder="会员昵称 / 手机号 / 会员 ID"
          clearable
          @enter="list.search()"
        />
      </Field>
    </template>

    <template #cell-displayName="{ row }">
      <span class="cp-member">{{ row.displayName }}</span>
      <span class="cp-member-id mono">{{ row.memberId.slice(0, 8) }}</span>
    </template>

    <template #cell-phone="{ row }">{{ row.phone || '—' }}</template>

    <template #cell-weekKey="{ row }">
      <Tag tone="neutral">{{ row.weekKey }}</Tag>
    </template>

    <template #cell-amount="{ row }">
      <span class="mono tnum cp-amount">{{ money(row.amount) }}</span>
    </template>

    <template #cell-status="{ row }">
      <Tag :tone="statusTone(row.status)" dot>{{ statusText(row.status) }}</Tag>
    </template>

    <template #cell-claimedAt="{ row }">
      <span class="mono muted">{{ row.claimedAt }}</span>
    </template>

    <template #cell-usedAt="{ row }">
      <span class="mono muted">{{ row.usedAt || '—' }}</span>
    </template>

    <template #cell-orderNumber="{ row }">
      <span v-if="row.orderNumber" class="mono">{{ row.orderNumber }}</span>
      <span v-else class="muted">—</span>
    </template>

    <template #cell-discountAmount="{ row }">
      <span v-if="Number(row.discountAmount) > 0" class="mono tnum">{{ money(row.discountAmount) }}</span>
      <span v-else class="muted">—</span>
    </template>
  </ListPage>
</template>

<style scoped>
.cp-config { padding: 18px 18px 8px; }
.cp-config-title { font-size: 15px; font-weight: 500; }
.cp-config-note {
  margin: 8px 0 16px;
  color: var(--ink-soft, #6f675c);
  font-size: 12px;
  line-height: 1.6;
}
.cp-stats {
  display: grid;
  gap: 6px;
  margin: 0 0 18px;
}
.cp-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--line, #ece7db);
  border-radius: 8px;
  background: var(--card-sub, #faf8f3);
}
.cp-stat dt { font-size: 12px; color: var(--ink-soft, #6f675c); }
.cp-stat dd { margin: 0; font-size: 15px; color: var(--ink, #191710); }

/* ---- 券面预览：深色底 + 金色面额，与点单端券包首屏同一套语言 ---- */
.cp-preview {
  padding: 14px;
  border-radius: 10px;
  color: #e9d191;
  background: radial-gradient(circle at 90% 10%, #3a352a 0, transparent 42%),
    linear-gradient(135deg, #1b1915 0%, #262219 58%, #141210 100%);
  background-color: #191710;
  border: 1px solid rgba(233, 209, 145, 0.28);
}
.cp-preview-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cp-preview-kicker {
  font-size: 9px;
  letter-spacing: 1.2px;
  color: rgba(233, 209, 145, 0.6);
}
.cp-preview-badge {
  padding: 1px 8px;
  border: 1px solid rgba(233, 209, 145, 0.4);
  border-radius: 999px;
  font-size: 9px;
  color: #e9d191;
}
.cp-preview-face {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-top: 12px;
}
.cp-preview-amount {
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
  color: #e9d191;
}
.cp-preview-amount i {
  font-size: 15px;
  font-style: normal;
  margin-right: 1px;
}
.cp-preview-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11px;
  color: #ded4bd;
}
.cp-preview-meta em {
  font-style: normal;
  font-size: 10px;
  color: rgba(233, 209, 145, 0.62);
}
.cp-preview-caption {
  margin: 6px 0 16px;
  color: var(--ink-faint, #8a8477);
  font-size: 11px;
  text-align: center;
}

.cp-member { display: block; }
.cp-member-id { font-size: 11px; color: var(--ink-faint, #8a8477); }
.cp-amount { color: var(--ink, #191710); font-weight: 500; }
</style>
