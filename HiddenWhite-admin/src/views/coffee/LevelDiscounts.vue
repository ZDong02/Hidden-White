<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Panel, Switch } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import { levelDiscountApi, type LevelDiscountOverview } from '@/api'
import { pushToast, toastError } from '@/composables/toast'
import { hasPermission } from '@/directives/permission'

/**
 * 会员等级折扣配置页。
 *
 * <p>矩阵形态（行 = 等级，列 = 周一至周日）而不是「一个等级一个表单」：
 * 运营调整时看的往往是「哪一天谁最便宜」，纵向对比一个等级的七天、
 * 横向对比同一天各等级，都只有矩阵做得到。逐个等级点进去改，改完就忘了别的等级是什么价。
 *
 * <p><b>页面上按「折」输入（8.5），接口按比率（0.85）传输</b>：换算只发生在
 * {@link toRate} / {@link toTenths} 两个函数里。散布在各处的 `8.5 / 10`
 * 迟早会有一处漏写，而这类错误表现为「打了 85 折」—— 门店不会立刻发现。
 */
interface EditRow {
  levelKey: string
  levelName: string
  minGrowth: number
  memberCount: number
  /** 是否已有策略行；没有时不能直接开关（后端会 404） */
  configured: boolean
  enabled: boolean
  /** 基准折扣，按「折」存放：'8.5' 表示 8.5 折 */
  baseTenths: string
  minSpend: string
  note: string
  /** weekday(1-7) → 折；空串表示继承基准 */
  daily: Record<number, string>
  saving: boolean
  errors: Record<string, string>
}

const overview = ref<LevelDiscountOverview | null>(null)
const rows = ref<EditRow[]>([])
const loading = ref(false)

const weekdays = computed(() => overview.value?.weekdays ?? [])
const canEdit = computed(() => hasPermission('coffee:leveldiscount:edit'))

/** 阶梯外的等级：这些会员永远拿不到折扣，必须在页面上显式提示 */
const orphanText = computed(() => {
  const orphans = overview.value?.orphanLevels ?? []
  return orphans.map(item => `${item.levelKey}（${item.memberCount} 人）`).join('、')
})

function toTenths(rate: number | string): string {
  const value = Number(rate)
  if (!Number.isFinite(value)) return ''
  // 0.85 → 8.5；0.9 → 9（不要 9.0）
  return String(Number((value * 10).toFixed(2)))
}

function toRate(tenths: string | number): number | null {
  const value = Number(tenths)
  if (!Number.isFinite(value)) return null
  return Number((value / 10).toFixed(3))
}

async function load() {
  loading.value = true
  try {
    const data = await levelDiscountApi.overview()
    overview.value = data
    rows.value = data.levels.map((level) => {
      const daily: Record<number, string> = {}
      for (let weekday = 1; weekday <= 7; weekday++) {
        const override = level.dailyRates?.[String(weekday)]
        daily[weekday] = override == null ? '' : toTenths(override)
      }
      return {
        levelKey: level.levelKey,
        levelName: level.levelName,
        minGrowth: level.minGrowth,
        memberCount: level.memberCount,
        configured: level.configured,
        enabled: level.enabled,
        baseTenths: toTenths(level.baseRate),
        minSpend: String(Number(level.minSpend ?? 0)),
        note: level.note ?? '',
        daily,
        saving: false,
        errors: {},
      }
    })
  } catch (error) {
    toastError(error, '会员折扣加载失败')
  } finally {
    loading.value = false
  }
}

/** 与后端同一套边界：折扣 1–10 折、门槛非负。前端只是第一道，服务端仍是最终判定。 */
function validate(row: EditRow): boolean {
  const errors: Record<string, string> = {}
  const base = toRate(row.baseTenths)
  if (base == null) errors.base = '请填写基准折扣'
  else if (base < 0.1 || base > 1) errors.base = '需在 1–10 折之间'

  const spend = Number(row.minSpend)
  if (!Number.isFinite(spend) || spend < 0) errors.minSpend = '不能为负数'

  for (const [weekday, value] of Object.entries(row.daily)) {
    if (String(value).trim() === '') continue
    const rate = toRate(value)
    if (rate == null || rate < 0.1 || rate > 1) errors[`daily.${weekday}`] = '需在 1–10 折之间'
  }
  row.errors = errors
  return Object.keys(errors).length === 0
}

async function save(row: EditRow) {
  if (!validate(row)) {
    pushToast('请先修正标红的格子', 'danger')
    return
  }
  const dailyRates: Record<string, number> = {}
  for (const [weekday, value] of Object.entries(row.daily)) {
    if (String(value).trim() === '') continue
    dailyRates[weekday] = toRate(value) as number
  }

  row.saving = true
  try {
    await levelDiscountApi.save({
      levelKey: row.levelKey,
      enabled: row.enabled,
      baseRate: toRate(row.baseTenths) as number,
      minSpend: Number(row.minSpend),
      note: row.note.trim(),
      dailyRates,
    })
    pushToast(`${row.levelName} 折扣已保存，点单端实时生效`)
    await load()
  } catch (error) {
    toastError(error, '保存失败')
  } finally {
    row.saving = false
  }
}

/**
 * 启停。
 *
 * <p>未保存过的等级不能直接开关：后端按 levelKey 定位策略行，
 * 行不存在时返回 404。这里给一句人话而不是把错误码甩给运营。
 */
async function toggle(row: EditRow, next: boolean) {
  if (!row.configured) {
    pushToast('请先保存该等级的折扣配置，再启用或关闭', 'info')
    return
  }
  try {
    await levelDiscountApi.setEnabled({ levelKey: row.levelKey, enabled: next })
    row.enabled = next
    pushToast(next ? `${row.levelName} 已启用折扣` : `${row.levelName} 已关闭折扣`)
  } catch (error) {
    // 受控开关：不改 model-value，界面自动回到原状态
    toastError(error, '切换失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">会员折扣</h2>
        <p class="page-desc">
          按等级配置全场折扣，可为一周内每一天单独设定（会员日、周末价）。结算时自动生效，
          与优惠券叠加；与首单礼遇互斥，系统取更优惠的一边。
        </p>
      </div>
      <span class="page-meta">共 {{ overview?.totalMembers ?? 0 }} 位会员</span>
    </div>

    <!-- 阶梯外的等级是真实数据事故：会员被手工改成「金卡会员」这类值，永远拿不到折扣 -->
    <div v-if="overview?.orphanLevels?.length" class="orphan">
      <strong>发现 {{ overview.orphanLevels.length }} 个不在成长值阶梯中的等级</strong>
      <span class="orphan-list">{{ orphanText }}</span>
      <span class="orphan-tip">
        这些会员拿不到任何折扣，也无法被成长值晋升覆盖。请到「会员管理」把他们改回阶梯内的等级。
      </span>
    </div>

    <Panel title="等级 × 星期折扣" class="matrix-panel">
      <div class="matrix-scroll">
        <div class="matrix" :class="{ 'is-loading': loading }">
          <div class="mrow mhead">
            <span class="c-level">等级</span>
            <span class="c-num">成员</span>
            <span class="c-switch">启用</span>
            <span class="c-rate">基准</span>
            <span class="c-rate">门槛</span>
            <span
              v-for="day in weekdays"
              :key="day.weekday"
              class="c-day"
              :class="{ 'is-today': day.weekday === overview?.todayWeekday }"
            >{{ day.label }}</span>
            <span class="c-action"></span>
          </div>

          <div v-for="row in rows" :key="row.levelKey" class="mrow">
            <div class="c-level">
              <span class="lv-name">{{ row.levelName }}</span>
              <span class="lv-growth">{{ row.minGrowth }} 成长值起</span>
            </div>

            <span class="c-num mono">{{ row.memberCount }}</span>

            <div class="c-switch">
              <Switch
                :model-value="row.enabled"
                :label="row.enabled ? '开' : '关'"
                @update:model-value="(value: boolean) => toggle(row, value)"
              />
            </div>

            <div class="c-rate">
              <input
                v-model="row.baseTenths"
                class="cell-input"
                :class="{ 'is-error': !!row.errors.base }"
                inputmode="decimal"
                aria-label="基准折扣"
              />
              <span class="cell-unit">折</span>
            </div>

            <div class="c-rate">
              <input
                v-model="row.minSpend"
                class="cell-input"
                :class="{ 'is-error': !!row.errors.minSpend }"
                inputmode="decimal"
                aria-label="折扣门槛"
              />
              <span class="cell-unit">元</span>
            </div>

            <div
              v-for="day in weekdays"
              :key="day.weekday"
              class="c-day"
              :class="{ 'is-today': day.weekday === overview?.todayWeekday }"
            >
              <input
                v-model="row.daily[day.weekday]"
                class="cell-input cell-input--day"
                :class="{ 'is-error': !!row.errors[`daily.${day.weekday}`] }"
                inputmode="decimal"
                placeholder="—"
                :aria-label="`${day.label}折扣`"
              />
            </div>

            <div class="c-action">
              <PermissionButton
                perm="coffee:leveldiscount:edit"
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

      <p class="matrix-hint">
        表格按「折」填写：8.5 表示 8.5 折，10 表示原价。星期列留空表示跟随基准值；
        为某天单独填写即覆盖该天（例如把周三设成 8.8 折的会员日）。
      </p>
    </Panel>

    <Panel title="生效规则" class="rule-panel">
      <ul class="rule-list">
        <li><b>等级来源</b>：等级由成长值阶梯决定（普通 0 / 白银 500 / 黄金 1500 / 铂金 4000 / 钻石 10000），只升不降。</li>
        <li><b>当日折扣</b>：先看该等级今天有没有单独配置，没有就用基准值；<b>启用关闭或折扣为 10 折时按原价结算</b>。</li>
        <li><b>门槛</b>：按订单商品金额判定，不含包装费；填 0 表示无门槛。</li>
        <li><b>与首单礼遇</b>：互斥，系统取优惠金额更大的一边（新客不吃亏）。</li>
        <li><b>与优惠券</b>：叠加，券在<b>折后金额</b>上抵扣，券的门槛仍按原价判定。</li>
        <li><b>适用范围</b>：点单端到店自提订单；积分兑换单不参与。</li>
      </ul>
    </Panel>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.page-title { font-size: 22px; }
.page-desc {
  max-width: 760px;
  margin-top: 6px;
  color: var(--ink-soft, #6f675c);
  font-size: 13px;
  line-height: 1.65;
}
.page-meta { flex-shrink: 0; color: var(--ink-faint, #8a8477); font-size: 12px; }

/* ---- 孤儿等级提示 ---- */
.orphan {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(180, 120, 40, 0.35);
  border-radius: 10px;
  background: rgba(180, 120, 40, 0.08);
  font-size: 12.5px;
}
.orphan strong { color: var(--ink, #191710); }
.orphan-list { color: var(--ink, #191710); font-weight: var(--fw-medium); }
.orphan-tip { color: var(--ink-soft, #6f675c); }

/* ---- 矩阵 ---- */
.matrix-panel { margin-bottom: 16px; }
.matrix-scroll { overflow-x: auto; }
.matrix { min-width: 1040px; transition: opacity 0.2s ease; }
.matrix.is-loading { opacity: 0.5; }

.mrow {
  display: grid;
  grid-template-columns: 180px 60px 76px 86px 86px repeat(7, 66px) 84px;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line, rgba(0, 0, 0, 0.06));
}
.mrow:last-child { border-bottom: none; }

.mhead {
  padding-bottom: 8px;
  color: var(--ink-soft, #6f675c);
  font-size: 11.5px;
  letter-spacing: 0.3px;
}
.mhead .c-day.is-today { color: var(--ink, #191710); font-weight: var(--fw-semibold); }
.mhead .c-day.is-today::after {
  content: '今天';
  display: block;
  margin-top: 2px;
  font-size: 9.5px;
  font-weight: var(--fw-regular);
  color: var(--ink-faint, #8a8477);
}

.c-level { display: flex; flex-direction: column; gap: 2px; }
.lv-name { color: var(--ink, #191710); font-size: 13.5px; font-weight: var(--fw-medium); }
.lv-growth { color: var(--ink-faint, #8a8477); font-size: 11px; }
.c-num { color: var(--ink, #191710); font-size: 13px; }
.c-switch { display: flex; justify-content: flex-start; }
.c-day { display: flex; align-items: center; }
.c-action { display: flex; justify-content: flex-end; }

/* 单元格输入：与表单输入同一套边框/底色，但在 66px 的格子里必须更紧凑 */
.cell-input {
  width: 100%;
  min-width: 0;
  height: 30px;
  padding: 0 6px;
  border: 1px solid var(--line-strong, #d9d3c4);
  border-radius: 6px;
  background: var(--input-bg, #fff);
  color: var(--ink, #191710);
  font-family: inherit;
  font-size: 13px;
  text-align: center;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.cell-input:focus {
  outline: none;
  border-color: var(--ink, #191710);
  box-shadow: 0 0 0 3px var(--gold-glow, rgba(180, 140, 60, 0.16));
}
.cell-input.is-error {
  border-color: var(--red-text, #b5523c);
  box-shadow: 0 0 0 3px rgba(181, 82, 60, 0.12);
}
.cell-input--day { width: 58px; }
.cell-unit {
  margin-left: 4px;
  color: var(--ink-faint, #8a8477);
  font-size: 11px;
}

.matrix-hint {
  margin-top: 12px;
  color: var(--ink-soft, #6f675c);
  font-size: 12px;
  line-height: 1.7;
}

/* 门槛列带单位，输入框要缩一点 */
.c-rate { display: flex; align-items: center; min-width: 0; }

/* ---- 规则说明 ---- */
.rule-panel { max-width: 980px; }
.rule-list {
  display: grid;
  gap: 8px;
  padding-left: 18px;
  color: var(--ink-soft, #6f675c);
  font-size: 12.5px;
  line-height: 1.7;
}
.rule-list b { color: var(--ink, #191710); font-weight: var(--fw-medium); }
</style>
