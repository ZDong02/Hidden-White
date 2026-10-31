<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Button, EmptyState, Field, PageHeader, Panel, Select, SpecPlate, Switch, Tag, TextInput } from '@/components/ui'
import { FormDialog, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeRecommendCandidate, type CoffeeRecommendRow } from '@/api'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'

/* ============================================================
   首页推荐商品
   ------------------------------------------------------------
   配置的是「推荐池」，不是「首页那三屏」：点单端每次冷启动 /
   下拉刷新时从池子里随机抽最多 3 款做轮播。因此这里有两条容易误解的规则，
   已经写进页面说明里：
     · 池子里超过 3 款时，本次没被抽到的商品也会在下次出现；
     · 停用推荐位不会删除配置，重新启用即可回到池子里。
   ============================================================ */

/** 每屏展示几款：与点单端 utils/recommend.js 的 SLIDE_COUNT 对齐 */
const SLIDES_PER_VIEW = 3

/** 文案长度上界：与 recommend_products 的列宽一致（超出会被后端拒绝） */
const MAX_LENGTH = { eyebrow: 40, headline: 40, caption: 60, linkText: 40 } as const

const rows = ref<CoffeeRecommendRow[]>([])
const candidates = ref<CoffeeRecommendCandidate[]>([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)

/** 预览聚焦哪一条：鼠标移到某行就预览它；否则取第一条启用中的 */
const focusId = ref<number | null>(null)

const previewRow = computed(
  () => rows.value.find(row => row.id === focusId.value)
    ?? rows.value.find(row => row.enabled && row.productEnabled)
    ?? rows.value[0]
    ?? null,
)

const activeCount = computed(() => rows.value.filter(row => row.enabled && row.productEnabled).length)

const specRows = computed(() => [
  { label: '池内推荐', value: rows.value.length },
  { label: '可用（在售）', value: activeCount.value },
  { label: '每屏展示', value: SLIDES_PER_VIEW },
])

/**
 * 预览文案的兜底规则与后端 CatalogService 完全一致：
 * 眉标→「本周推荐」、主标题→商品名、短句→商品副标题、链接词→「品味+商品名」。
 * 保持一致是为了让运营在预览里看到的就是点单端上线的样子。
 */
const preview = computed(() => {
  const row = previewRow.value
  if (!row) return null
  return {
    productName: row.productName,
    eyebrow: row.eyebrow || '本周推荐',
    headline: row.headline || row.productName,
    caption: row.caption || '（未填短句，前台用商品副标题）',
    linkText: row.linkText || `品味${row.productName}`,
    price: Number(row.price),
    image: row.productImage,
    usable: row.enabled && row.productEnabled,
  }
})

/** 只有管理端上传的图片（/uploads/**）在后台可直接加载；包内静态图在后台不可见 */
function uploadedImage(path: string) {
  if (!path) return ''
  return path.startsWith('/uploads/') || /^https?:\/\//i.test(path) ? path : ''
}

/** 三行文案合成一句，列表里一眼能核对完 */
function copyLine(row: CoffeeRecommendRow) {
  const parts = [row.eyebrow, row.headline, row.caption].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未填文案，前台全部用商品字段兜底'
}

async function load() {
  loading.value = true
  try {
    rows.value = await coffeeApi.recommendations.list()
  } catch (error) {
    toastError(error, '推荐位加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCandidates() {
  try {
    candidates.value = await coffeeApi.recommendations.candidates()
  } catch (error) {
    // 候选拉不到不阻断：编辑时下拉里仍能保住当前商品
    console.warn('候选商品加载失败', error)
  }
}

interface RecommendForm {
  id: number | null
  productId: string
  eyebrow: string
  headline: string
  caption: string
  linkText: string
  sortOrder: number
  enabled: boolean
}

const emptyForm = (): RecommendForm => ({
  id: null,
  productId: '',
  eyebrow: '',
  headline: '',
  caption: '',
  linkText: '',
  sortOrder: rows.value.length + 1,
  enabled: true,
})

const form = reactive<RecommendForm>(emptyForm())
const errors = ref<Partial<Record<keyof RecommendForm, string>>>({})

/**
 * 下拉选项：候选商品（在售且未进池）+ 编辑中的当前商品。
 * 当前商品本就在池里，后端候选接口会把它排除，不手动补回就会「编辑时看不到自己」。
 */
const productOptions = computed(() => {
  const options = candidates.value.map(item => ({
    label: `${item.name} · ¥${item.price}`,
    value: item.id,
  }))
  const current = rows.value.find(row => row.id === form.id)
  if (current && !candidates.value.some(item => item.id === current.productId)) {
    options.unshift({
      label: `${current.productName} · ¥${current.price}（当前）`,
      value: current.productId,
    })
  }
  return options
})

/** 表单里选中的商品，用于显示「这台商品现在卖多少钱」的提示 */
const selectedProduct = computed(() =>
  candidates.value.find(item => item.id === form.productId)
  ?? rows.value.find(row => row.productId === form.productId),
)

async function openCreate() {
  Object.assign(form, emptyForm())
  errors.value = {}
  dialog.value = true
  await loadCandidates()
}

async function openEdit(row: CoffeeRecommendRow) {
  Object.assign(form, {
    id: row.id,
    productId: row.productId,
    eyebrow: row.eyebrow,
    headline: row.headline,
    caption: row.caption,
    linkText: row.linkText,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
  })
  errors.value = {}
  dialog.value = true
  await loadCandidates()
}

function validateForm() {
  const next: Partial<Record<keyof RecommendForm, string>> = {}
  if (!form.productId) next.productId = '请选择要推荐的商品'
  // 长度在输入时已由 maxlength 限制，这里只兜住粘贴超长的极端情况
  if (form.eyebrow.trim().length > MAX_LENGTH.eyebrow) next.eyebrow = `眉标最多 ${MAX_LENGTH.eyebrow} 个字`
  if (form.headline.trim().length > MAX_LENGTH.headline) next.headline = `主标题最多 ${MAX_LENGTH.headline} 个字`
  if (form.caption.trim().length > MAX_LENGTH.caption) next.caption = `短句最多 ${MAX_LENGTH.caption} 个字`
  if (form.linkText.trim().length > MAX_LENGTH.linkText) next.linkText = `链接文案最多 ${MAX_LENGTH.linkText} 个字`
  errors.value = next
  return Object.keys(next).length === 0
}

async function submit() {
  if (!validateForm()) return
  saving.value = true
  const payload = {
    productId: form.productId,
    eyebrow: form.eyebrow.trim(),
    headline: form.headline.trim(),
    caption: form.caption.trim(),
    linkText: form.linkText.trim(),
    sortOrder: Number(form.sortOrder) || 0,
    enabled: form.enabled,
  }
  try {
    if (form.id === null) {
      await coffeeApi.recommendations.create(payload)
      pushToast('推荐已添加')
    } else {
      await coffeeApi.recommendations.update(form.id, payload)
      pushToast('推荐已更新')
    }
    dialog.value = false
    await load()
  } catch (error) {
    toastError(error, '推荐保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: CoffeeRecommendRow) {
  try {
    await coffeeApi.recommendations.setEnabled(row.id, !row.enabled)
    await load()
  } catch (error) {
    toastError(error, '状态修改失败')
  }
}

async function remove(row: CoffeeRecommendRow) {
  const ok = await confirm({
    title: '移除推荐',
    message: `确认把「${row.productName}」从首页轮播里移除吗？商品本身不会被删除。`,
    confirmText: '移除',
    danger: true,
  })
  if (!ok) return
  try {
    await coffeeApi.recommendations.remove(row.id)
    pushToast('已从推荐池移除')
    await load()
  } catch (error) {
    toastError(error, '移除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader
      title="推荐商品"
      desc="首页轮播位。点单端进入首页时从推荐池里随机抽 3 款展示，保存后点单端下次拉取即生效。"
    >
      <template #spec>
        <SpecPlate title="推荐池" :rows="specRows" />
      </template>
      <PermissionButton perm="coffee:recommend:add" variant="primary" icon="plus" @click="openCreate">
        新增推荐
      </PermissionButton>
    </PageHeader>

    <div class="rec-layout">
      <!-- ── 推荐池 ─────────────────────────────────────────── -->
      <Panel title="推荐池" :meta="`共 ${rows.length} 条`">
        <template #tools>
          <Button size="sm" variant="ghost" icon="refresh" :loading="loading" @click="load">刷新</Button>
        </template>

        <div v-if="loading && !rows.length" class="rec-skeletons" aria-hidden="true">
          <div v-for="n in 3" :key="`skeleton-${n}`" class="rec-skeleton" />
        </div>

        <EmptyState
          v-else-if="!rows.length"
          icon="star"
          title="还没有推荐商品"
          desc="添加后，首页原来那句「一杯，恰好。」的位置会换成这三款商品的轮播。"
        >
          <PermissionButton perm="coffee:recommend:add" variant="primary" icon="plus" size="sm" @click="openCreate">
            新增推荐
          </PermissionButton>
        </EmptyState>

        <ul v-else class="rec-list">
          <li
            v-for="(row, index) in rows"
            :key="row.id"
            :class="['rec-row', { 'is-off': !row.enabled, 'is-broken': !row.productEnabled, 'is-previewed': previewRow?.id === row.id }]"
            @mouseenter="focusId = row.id"
            @focusin="focusId = row.id"
          >
            <span class="rec-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>

            <span class="rec-thumb" aria-hidden="true">
              <img v-if="uploadedImage(row.productImage)" :src="uploadedImage(row.productImage)" alt="" />
              <span v-else class="rec-thumb-mono">{{ row.productName.slice(0, 1) }}</span>
            </span>

            <div class="rec-main">
              <div class="rec-title-line">
                <h3 class="rec-name">{{ row.productName }}</h3>
                <span class="code-chip">{{ row.productId }}</span>
                <span class="rec-price mono">¥{{ row.price }}</span>
              </div>
              <p class="rec-copy">{{ copyLine(row) }}</p>
            </div>

            <div class="rec-side">
              <div class="rec-flags">
                <Tag v-if="!row.productEnabled" tone="danger" dot>商品已下架</Tag>
                <Tag v-else :tone="row.enabled ? 'success' : 'neutral'">{{ row.enabled ? '已启用' : '已停用' }}</Tag>
                <span class="rec-sort mono">排序 {{ row.sortOrder }}</span>
              </div>
              <div class="rec-ops">
                <Switch
                  :model-value="row.enabled"
                  :aria-label="`${row.enabled ? '停用' : '启用'} ${row.productName}`"
                  @update:model-value="toggleEnabled(row)"
                />
                <PermissionButton perm="coffee:recommend:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
                <PermissionButton perm="coffee:recommend:remove" icon-only icon="trash" label="移除" danger @click="remove(row)" />
              </div>
            </div>
          </li>
        </ul>

        <p class="rec-footnote">
          点单端按随机顺序展示，最多 {{ SLIDES_PER_VIEW }} 款；池内不足 {{ SLIDES_PER_VIEW }} 款时按实际数量展示。
          停用不会删除配置，重新启用即回到池子。
        </p>
      </Panel>

      <!-- ── 首页预览 ───────────────────────────────────────── -->
      <aside class="rec-aside">
        <Panel title="首页预览" :meta="preview ? '1:1 尺寸' : ''">
          <div v-if="!preview" class="preview-empty">
            <p class="preview-empty-text">添加推荐后，这里会实时显示首页第一屏的样子。</p>
          </div>

          <div v-else class="preview-stage">
            <div :class="['preview-card', { 'is-dim': !preview.usable }]">
              <div class="preview-copy">
                <div class="preview-eyebrow-row">
                  <span class="preview-rule" />
                  <span class="preview-eyebrow">{{ preview.eyebrow }}</span>
                </div>
                <p class="preview-headline">{{ preview.headline }}</p>
                <p class="preview-caption">{{ preview.caption }}</p>
                <div class="preview-foot">
                  <span class="preview-link">{{ preview.linkText }}</span>
                  <span class="preview-price">¥{{ preview.price }}</span>
                </div>
              </div>
              <div class="preview-media">
                <img v-if="uploadedImage(preview.image)" :src="uploadedImage(preview.image)" alt="" />
    <span v-else class="preview-media-mono">{{ preview.productName.slice(0, 1) }}</span>
              </div>
              <div class="preview-dots" aria-hidden="true">
                <span v-for="n in Math.min(SLIDES_PER_VIEW, Math.max(rows.length, 1))" :key="`dot-${n}`" :class="['preview-dot', n === 1 && 'is-on']" />
              </div>
            </div>
            <p v-if="!preview.usable" class="preview-warn">
              这一条当前不会出现在点单端{{ previewRow?.enabled ? '：商品已下架' : '：推荐位已停用' }}。
            </p>
            <p v-else class="preview-hint">悬停左侧任意一条可切换预览。价格与图片随商品实时变化。</p>
          </div>
        </Panel>

        <div class="rec-tips m-surface">
          <h4 class="rec-tips-title">配置说明</h4>
          <ul>
            <li>文案留空即用商品字段兜底：主标题→商品名、短句→副标题、链接词→品味+商品名。</li>
            <li>同一商品只占一个推荐位；想换商品请直接编辑这一条。</li>
            <li>商品下架后这一条会自动从轮播里消失，重新上架即恢复。</li>
          </ul>
        </div>
      </aside>
    </div>

    <FormDialog
      :open="dialog"
      :title="form.id === null ? '新增推荐' : '编辑推荐'"
      sub="文案可留空，留空处前台会用商品自身的字段补齐"
      :saving="saving"
      @close="dialog = false"
      @submit="submit"
    >
      <Field
        label="推荐商品"
        required
        span
        :error="errors.productId"
        :hint="selectedProduct ? `当前售价 ¥${selectedProduct.price}，轮播上显示的就是这个价` : '只列出在售且尚未进入推荐池的商品'"
      >
        <Select v-model="form.productId" :options="productOptions" placeholder="选择一款在售商品" filterable />
      </Field>
      <Field label="眉标" :error="errors.eyebrow" :hint="`留空显示「本周推荐」；单行展示，建议 16 字以内（最多 ${MAX_LENGTH.eyebrow} 字）`">
        <TextInput v-model="form.eyebrow" placeholder="本周推荐 · 匠心双份浓缩" :maxlength="MAX_LENGTH.eyebrow" :invalid="!!errors.eyebrow" />
      </Field>
      <Field label="链接文案" :error="errors.linkText" hint="右下角的动作文字，如「品味招牌馥白」；建议 8 字以内">
        <TextInput v-model="form.linkText" placeholder="品味招牌馥白" :maxlength="MAX_LENGTH.linkText" :invalid="!!errors.linkText" />
      </Field>
      <Field label="主标题" span :error="errors.headline" :hint="`留空用商品名；单行展示，建议 6 字以内，超出末尾显示省略号（最多 ${MAX_LENGTH.headline} 字）`">
        <TextInput v-model="form.headline" placeholder="一杯，恰好。" :maxlength="MAX_LENGTH.headline" :invalid="!!errors.headline" />
      </Field>
      <Field label="短句" span :error="errors.caption" :hint="`留空用商品副标题；单行展示，建议 12 字以内（最多 ${MAX_LENGTH.caption} 字）`">
        <TextInput v-model="form.caption" placeholder="细腻奶感，纯粹咖啡香。" :maxlength="MAX_LENGTH.caption" :invalid="!!errors.caption" />
      </Field>
      <Field label="排序（小的在前）" hint="仅决定随机抽样的基准顺序，不保证展示顺序">
        <TextInput v-model="form.sortOrder" type="number" />
      </Field>
      <Field label="状态" group>
        <Switch v-model="form.enabled" :label="form.enabled ? '启用（进入轮播池）' : '停用（保留配置）'" />
      </Field>
    </FormDialog>
  </div>
</template>

<style scoped>
/* ── 两栏：推荐池 + 预览 ───────────────────────────────────── */
.rec-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 372px;
  gap: 16px;
  align-items: start;
  margin-top: 18px;
}

.rec-aside {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

@media (max-width: 1180px) {
  .rec-layout { grid-template-columns: minmax(0, 1fr); }
  .rec-aside { position: static; }
}

/* ── 推荐行 ────────────────────────────────────────────────── */
.rec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.rec-row {
  display: grid;
  grid-template-columns: 28px 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--r-md);
  transition: background-color 0.18s ease, border-color 0.18s ease, opacity 0.18s ease;
}

.rec-row:hover,
.rec-row.is-previewed {
  border-color: var(--line-strong);
  background: var(--hover-bg);
}

/* 停用 / 商品下架：整行降透明度，但保留可操作（开关还得点得动） */
.rec-row.is-off { opacity: 0.62; }
.rec-row.is-broken { border-color: var(--red-soft); background: var(--red-soft); }

.rec-index {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-faint);
  letter-spacing: 0.04em;
}

.rec-thumb {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: var(--groove-bg);
}

.rec-thumb img { width: 100%; height: 100%; object-fit: cover; }

/* 包内静态图在后台不可见，用首字做占位：比裂图体面，也能对上号 */
.rec-thumb-mono {
  font-family: "Songti SC", "Noto Serif SC", serif;
  font-size: 17px;
  color: var(--ink-soft);
}

.rec-main { min-width: 0; }

.rec-title-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.rec-name {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rec-price { flex: none; font-size: 11.5px; color: var(--ink-muted); }

.rec-copy {
  overflow: hidden;
  margin-top: 3px;
  font-size: 12px;
  color: var(--ink-soft);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rec-side {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rec-flags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.rec-sort { font-size: 10.5px; color: var(--ink-faint); }

.rec-ops { display: flex; align-items: center; gap: 2px; }

.rec-footnote {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  font-size: 11.5px;
  color: var(--ink-faint);
  line-height: 1.6;
}

/* ── 骨架屏 ────────────────────────────────────────────────── */
.rec-skeletons { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }

.rec-skeleton {
  height: 64px;
  border-radius: var(--r-md);
  background: linear-gradient(100deg, var(--groove-bg) 20%, var(--hover-bg) 40%, var(--groove-bg) 60%);
  background-size: 240% 100%;
  animation: rec-shimmer 1.4s linear infinite;
}

@keyframes rec-shimmer {
  from { background-position: 100% 0; }
  to { background-position: -60% 0; }
}

/* ── 预览：1:1 复刻点单端首屏卡片 ───────────────────────────
   取点单端 tokens.scss 的色值（那边是深色主视觉，与后台浅色玻璃不是一套皮）。
   尺寸按 750rpx:375px = 2:1 换算：卡片 662×360rpx → 331×180px。 */
.preview-stage { margin-top: 12px; }

.preview-card {
  position: relative;
  width: 331px;
  height: 180px;
  box-sizing: border-box;
  padding: 20px 16px;
  overflow: hidden;
  border: 1px solid #b99737;
  border-radius: 11px;
  background: linear-gradient(150deg, #171713 0%, #0b0a07 100%);
  box-shadow: 0 10px 24px rgba(20, 16, 8, 0.28);
}

/* 深色卡右下的金色细环：点单端三张深色卡共用的品牌笔触 */
.preview-card::after {
  content: "";
  position: absolute;
  right: -36px;
  bottom: -68px;
  width: 156px;
  height: 156px;
  border: 1px solid rgba(212, 173, 47, 0.3);
  border-radius: 50%;
}

.preview-card.is-dim { opacity: 0.45; filter: grayscale(0.4); }

.preview-copy {
  position: relative;
  z-index: 1;
  display: flex;
  height: 100%;
  flex-direction: column;
}

/* 与点单端一致：眉标吃满整行（图片上沿在它下方），
   标题 / 短句 / 操作行才让出图片宽度，否则长眉标会被提前截断 */
.preview-headline,
.preview-caption,
.preview-foot {
  padding-right: 150px;
}

.preview-eyebrow-row { display: flex; align-items: center; gap: 6px; }

.preview-rule { width: 13px; height: 1px; flex: none; background: #d4ad2f; }

.preview-eyebrow {
  overflow: hidden;
  font-size: 11px;
  letter-spacing: 1px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #e9d191;
}

.preview-headline {
  display: -webkit-box;
  margin-top: 16px;
  overflow: hidden;
  font-family: "Songti SC", "Noto Serif SC", "Source Han Serif SC", serif;
  font-size: 22px;
  line-height: 1.12;
  color: #f5f0e3;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.preview-caption {
  display: -webkit-box;
  margin-top: 12px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: #b5ad99;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.preview-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 16px;
}

.preview-link {
  overflow: hidden;
  font-size: 11px;
  letter-spacing: 0.5px;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #e9d191;
}

.preview-price {
  flex: none;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #e9d191;
}

.preview-media {
  position: absolute;
  /* 与点单端同一几何关系：图片 268rpx / 距顶 52rpx / 距右 26rpx */
  top: 26px;
  right: 13px;
  /* 图片块压在装饰金环之上：与点单端一致（那边图片也是 z-index 1），
     否则环形描边会横穿商品图，看起来像一条划痕 */
  z-index: 1;
  display: grid;
  place-items: center;
  width: 134px;
  height: 134px;
  overflow: hidden;
  border: 1px solid rgba(212, 173, 47, 0.3);
  border-radius: 9px;
  background: linear-gradient(150deg, #241f15, #0b0a07);
}

.preview-media img { width: 100%; height: 100%; object-fit: cover; }

.preview-media-mono {
  font-family: "Songti SC", "Noto Serif SC", serif;
  font-size: 44px;
  color: rgba(233, 209, 145, 0.55);
}

.preview-dots {
  position: absolute;
  left: 16px;
  bottom: 10px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 4px;
}

.preview-dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: rgba(212, 173, 47, 0.3);
  transition: width 0.2s ease;
}

.preview-dot.is-on { width: 14px; background: #d4ad2f; }

.preview-hint,
.preview-warn,
.preview-empty-text {
  margin-top: 10px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ink-faint);
}

.preview-warn { color: var(--red-text); }

.preview-empty {
  display: grid;
  place-items: center;
  height: 180px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  text-align: center;
  padding: 0 20px;
}

/* ── 说明卡 ────────────────────────────────────────────────── */
.rec-tips { padding: 14px 16px; border-radius: var(--r-lg); }

.rec-tips-title { font-size: 12px; font-weight: 600; letter-spacing: 0.02em; }

.rec-tips ul {
  margin: 8px 0 0;
  padding-left: 16px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-soft);
}

@media (prefers-reduced-motion: reduce) {
  .rec-skeleton { animation: none; }
}
</style>
