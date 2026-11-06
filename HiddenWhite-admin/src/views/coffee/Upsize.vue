<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Panel, Switch } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import { upsizeApi, type UpsizeOverview, type UpsizeRecordRow } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import { hasPermission } from '@/directives/permission'

/**
 * 免费升杯配置页。
 *
 * <p><b>核心概念只有两个数字</b>：每月免费几次（{@code freeQuota}）与免费用完后
 * 每次多少积分（{@code pointsCost}）。低等级把免费次数设成 0、只给积分，
 * 高等级给免费次数 —— 这就是「低等级用积分换、高等级直接免费」的全部实现。
 *
 * <p><b>-1 在页面上显示成「不限」</b>：运营看到 -1 会以为配错了。
 * 输入框里仍允许填 -1，但旁边会明确标注。
 */
interface EditRow {
  levelKey: string
  levelName: string
  memberCount: number
  usedThisMonth: number
  sizeDiff: number | null
  sizeValid: boolean
  enabled: boolean
  monthlyLimit: string
  freeQuota: string
  pointsCost: string
  fromSize: string
  toSize: string
  note: string
  saving: boolean
}

const overview = ref<UpsizeOverview | null>(null)
const rows = ref<EditRow[]>([])
const loading = ref(false)
const canEdit = computed(() => hasPermission('coffee:upsize:edit'))
const sizes = computed(() => overview.value?.sizes ?? [])

const records = ref<UpsizeRecordRow[]>([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordLoading = ref(false)
const filterMonth = ref('')
const filterPayType = ref('')

/** 本月各档消耗合计：用来判断「额度是定松了还是定紧了」。 */
const monthUsage = computed(() => rows.value.reduce((sum, row) => sum + row.usedThisMonth, 0))
const invalidCount = computed(() => rows.value.filter((row) => !row.sizeValid).length)

function toText(value: number): string {
  return String(value)
}

async function load() {
  loading.value = true
  try {
    const data = await upsizeApi.overview()
    overview.value = data
    rows.value = data.levels.map((level) => ({
      levelKey: level.levelKey,
      levelName: level.levelName,
      memberCount: level.memberCount,
      usedThisMonth: level.usedThisMonth,
      sizeDiff: level.sizeDiff,
      sizeValid: level.sizeValid,
      enabled: level.enabled,
      monthlyLimit: toText(level.monthlyLimit),
      freeQuota: toText(level.freeQuota),
      pointsCost: toText(level.pointsCost),
      fromSize: level.fromSize,
      toSize: level.toSize,
      note: level.note,
      saving: false,
    }))
    if (!filterMonth.value) filterMonth.value = data.monthKey
  } catch (error) {
    toastError(error, '加载升杯策略失败')
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const data = await upsizeApi.records({
      pageNum: recordPage.value,
      pageSize: 10,
      monthKey: filterMonth.value,
      payType: filterPayType.value,
    })
    records.value = data.rows
    recordTotal.value = data.total
  } catch (error) {
    toastError(error, '加载使用记录失败')
  } finally {
    recordLoading.value = false
  }
}

/**
 * 保存一行。
 *
 * <p>前端只做「能不能转成数字」的检查；额度与免费次数的业务校验（总次数不能小于
 * 免费次数、积分价不能为负、杯型必须存在）放在服务端 ——
 * 两处各写一套必然出现「页面放行、接口拒绝」。
 */
async function save(row: EditRow) {
  const monthlyLimit = Number(row.monthlyLimit)
  const freeQuota = Number(row.freeQuota)
  const pointsCost = Number(row.pointsCost)
  if (![monthlyLimit, freeQuota, pointsCost].every((v) => Number.isFinite(v))) {
    pushToast('额度与积分价必须是数字（-1 表示不限）', 'danger')
    return
  }

  row.saving = true
  try {
    await upsizeApi.save({
      levelKey: row.levelKey,
      monthlyLimit,
      freeQuota,
      pointsCost,
      fromSize: row.fromSize,
      toSize: row.toSize,
      enabled: row.enabled,
      note: row.note,
    })
    pushToast(`${row.levelName} 的升杯策略已保存`, 'ok')
    await load()
  } catch (error) {
    toastError(error, '保存失败')
  } finally {
    row.saving = false
  }
}

/** 单字段启停：不复用整行保存，避免把没改的列一起写回。 */
async function toggle(row: EditRow, value: boolean) {
  const previous = row.enabled
  row.enabled = value
  try {
    await upsizeApi.save({ levelKey: row.levelKey, enabled: value })
    pushToast(value ? `${row.levelName} 升杯已开启` : `${row.levelName} 升杯已关闭`, 'ok')
  } catch (error) {
    row.enabled = previous
    toastError(error, '切换失败')
  }
}

function unlimited(value: string): boolean {
  return Number(value) === -1
}

function money(value: number | null): string {
  return value == null ? '—' : `¥${Number(value).toFixed(2)}`
}

function timeText(value: string | null): string {
  if (!value) return '—'
  // 后端返回 ISO 串（2026-11-06T17:56:50.5…），页面只要到分钟
  return value.replace('T', ' ').slice(0, 16)
}

function payTypeText(value: string): string {
  if (value === 'FREE') return '免费额度'
  if (value === 'POINTS') return '积分兑换'
  return value
}

function statusText(value: string): string {
  if (value === 'USED') return '已占用'
  if (value === 'RELEASED') return '已释放'
  return value
}

function goPage(delta: number) {
  const next = recordPage.value + delta
  if (next < 1) return
  if ((next - 1) * 10 >= recordTotal.value) return
  recordPage.value = next
  void loadRecords()
}

onMounted(async () => {
  await load()
  await loadRecords()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1 class="page-title">免费升杯</h1>
        <p class="page-desc">
          按等级配置「每月免费几次」与「免费用完后每次多少积分」。低等级只给积分、
          高等级给免费次数；结算时由顾客显式勾选，不会自动消耗额度。
        </p>
      </div>
      <span class="page-meta">{{ overview?.monthKey ?? '' }} · 本月已用 {{ monthUsage }} 次</span>
    </header>

    <!-- 杯型配错时该等级的升杯会静默失效，页面上必须显式报警 -->
    <div v-if="invalidCount" class="alert">
      <strong>有 {{ invalidCount }} 个等级的杯型配置无效</strong>
      <span>起始杯型或目标杯型在商品规格里不存在，该等级的升杯不会生效。请重新选择杯型。</span>
    </div>

    <Panel title="等级策略">
      <div class="table-scroll">
        <div class="table" :class="{ 'is-loading': loading }">
          <div class="trow thead">
            <span class="c-level">等级</span>
            <span class="c-num">成员</span>
            <span class="c-switch">启用</span>
            <span class="c-num">每月总次数</span>
            <span class="c-num">免费次数</span>
            <span class="c-num">积分价</span>
            <span class="c-size">起始杯型</span>
            <span class="c-size">目标杯型</span>
            <span class="c-num">差价</span>
            <span class="c-num">本月已用</span>
            <span class="c-note">说明</span>
            <span class="c-action"></span>
          </div>

          <div v-for="row in rows" :key="row.levelKey" class="trow">
            <div class="c-level">
              <span class="lv-name">{{ row.levelName }}</span>
              <span v-if="!row.sizeValid" class="lv-warn">杯型无效</span>
            </div>
            <span class="c-num mono">{{ row.memberCount }}</span>

            <div class="c-switch">
              <Switch
                :model-value="row.enabled"
                :label="row.enabled ? '开' : '关'"
                @update:model-value="(value: boolean) => toggle(row, value)"
              />
            </div>

            <div class="c-num">
              <input v-model="row.monthlyLimit" class="cell-input" inputmode="numeric" aria-label="每月总次数" />
              <span v-if="unlimited(row.monthlyLimit)" class="cell-hint">不限</span>
            </div>
            <div class="c-num">
              <input v-model="row.freeQuota" class="cell-input" inputmode="numeric" aria-label="每月免费次数" />
              <span v-if="unlimited(row.freeQuota)" class="cell-hint">不限</span>
            </div>
            <div class="c-num">
              <input v-model="row.pointsCost" class="cell-input" inputmode="numeric" aria-label="每次消耗积分" />
            </div>

            <div class="c-size">
              <select v-model="row.fromSize" class="cell-select" aria-label="起始杯型">
                <option v-for="size in sizes" :key="size" :value="size">{{ size }}</option>
              </select>
            </div>
            <div class="c-size">
              <select v-model="row.toSize" class="cell-select" aria-label="目标杯型">
                <option v-for="size in sizes" :key="size" :value="size">{{ size }}</option>
              </select>
            </div>

            <span class="c-num mono">{{ money(row.sizeDiff) }}</span>
            <span class="c-num mono">{{ row.usedThisMonth }}</span>

            <div class="c-note">
              <input v-model="row.note" class="cell-input cell-input--wide" aria-label="说明文案" />
            </div>

            <div class="c-action">
              <PermissionButton
                perm="coffee:upsize:edit"
                variant="secondary"
                size="sm"
                :loading="row.saving"
                :disabled="!canEdit"
                @click="save(row)"
              >
                保存
              </PermissionButton>
            </div>
          </div>
        </div>
      </div>
      <p class="table-tip">
        总次数与免费次数都填 -1 表示不限（钻石档的默认配置）。总次数是硬上限，不能小于免费次数。
      </p>
    </Panel>

    <Panel title="使用记录">
      <div class="filters">
        <input v-model="filterMonth" class="filter-input" placeholder="月份 如 2026-11" aria-label="按月筛选" />
        <select v-model="filterPayType" class="filter-select" aria-label="按结算方式筛选">
          <option value="">全部方式</option>
          <option value="FREE">免费额度</option>
          <option value="POINTS">积分兑换</option>
        </select>
        <button class="filter-btn" @click="goPage(0)">查询</button>
      </div>

      <div class="table-scroll">
        <div class="table table--records" :class="{ 'is-loading': recordLoading }">
          <div class="trow thead">
            <span class="c-time">时间</span>
            <span class="c-member">会员</span>
            <span class="c-level-sm">等级</span>
            <span class="c-pay">方式</span>
            <span class="c-points">积分</span>
            <span class="c-size-sm">杯型</span>
            <span class="c-order">订单号</span>
            <span class="c-status">状态</span>
          </div>
          <div v-for="row in records" :key="row.id" class="trow">
            <span class="c-time">{{ timeText(row.created_at) }}</span>
            <span class="c-member">{{ row.display_name || row.member_id.slice(0, 8) }}</span>
            <span class="c-level-sm">{{ row.level_key }}</span>
            <span class="c-pay">{{ payTypeText(row.pay_type) }}</span>
            <span class="c-points mono">{{ row.points_cost || '—' }}</span>
            <span class="c-size-sm">{{ row.size_from }} → {{ row.size_to }}</span>
            <span class="c-order mono">{{ row.order_number || '—' }}</span>
            <span class="c-status" :class="{ 'is-released': row.status === 'RELEASED' }">
              {{ statusText(row.status) }}
            </span>
          </div>
          <div v-if="!records.length && !recordLoading" class="trow trow--empty">
            <span>暂无使用记录</span>
          </div>
        </div>
      </div>

      <div class="pager">
        <span class="pager-info">共 {{ recordTotal }} 条</span>
        <button class="filter-btn" :disabled="recordPage <= 1" @click="goPage(-1)">上一页</button>
        <span class="pager-info">第 {{ recordPage }} 页</span>
        <button class="filter-btn" :disabled="recordPage * 10 >= recordTotal" @click="goPage(1)">下一页</button>
      </div>
    </Panel>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  max-width: 760px;
  color: var(--c-ink-muted);
  font-size: 13px;
  line-height: 1.7;
}

.page-meta {
  color: var(--c-ink-subtle);
  font-size: 13px;
  white-space: nowrap;
}

.alert {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border-radius: var(--r-md);
  background: rgba(196, 120, 60, 0.1);
  color: var(--c-ink);
  font-size: 13px;
}

.table-scroll {
  overflow-x: auto;
}

.table {
  min-width: 1080px;
}

.table.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

.trow {
  display: grid;
  grid-template-columns:
    120px 64px 72px 108px 100px 88px 160px 160px 80px 88px 1fr 84px;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--c-divider);
  font-size: 13px;
}

.thead {
  color: var(--c-ink-subtle);
  font-size: 12px;
  border-bottom-color: var(--c-border-strong);
}

.c-level {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.lv-name {
  font-weight: 600;
}

.lv-warn {
  color: var(--c-danger, #c0392b);
  font-size: 11px;
}

.mono {
  font-variant-numeric: tabular-nums;
}

.cell-input,
.cell-select {
  width: 100%;
  min-width: 0;
  padding: 4px 8px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  color: var(--c-ink);
  font-size: 13px;
}

.cell-input--wide {
  max-width: 240px;
}

.cell-hint {
  display: inline-block;
  margin-top: 2px;
  color: var(--c-gold-text, #a9812f);
  font-size: 11px;
}

.c-action {
  display: flex;
  justify-content: flex-end;
}

.table-tip {
  margin: 10px 0 0;
  color: var(--c-ink-subtle);
  font-size: 12px;
}

.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-input,
.filter-select {
  padding: 6px 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  color: var(--c-ink);
  font-size: 13px;
}

.filter-btn {
  padding: 6px 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface-2);
  color: var(--c-ink);
  font-size: 13px;
  cursor: pointer;
}

.filter-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table--records .trow {
  grid-template-columns: 130px 120px 90px 90px 64px 240px 140px 80px;
  min-width: 960px;
}

.trow--empty {
  display: flex;
  justify-content: center;
  color: var(--c-ink-subtle);
  padding: 24px 0;
}

.c-status.is-released {
  color: var(--c-ink-subtle);
}

.pager {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.pager-info {
  color: var(--c-ink-subtle);
  font-size: 12px;
}
</style>
