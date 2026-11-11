<template>
  <view class="screen level-screen">
    <PhoneHeader title="会员折扣" />

    <view class="level-body">
      <!-- 未登录：等级与成长值都是个人数据，静态表对顾客没有意义 -->
      <view v-if="!loggedIn" class="empty reveal">
        <Icon name="percent" tone="muted" :size="56" />
        <text class="muted empty-text">登录后查看你的等级与专属折扣</text>
        <UiButton variant="primary" class="empty-action" @tap="goLogin">去登录</UiButton>
      </view>

      <!-- 加载中：给骨架而不是空列表，否则首帧会闪一下「0 档」 -->
      <view v-else-if="viewState === 'loading'" class="skeleton-wrap reveal">
        <view class="skeleton skeleton-hero" />
        <view class="skeleton skeleton-row" />
        <view class="skeleton skeleton-row" />
      </view>

      <!--
        加载失败与「等级不在阶梯」是两件事，不能共用一句文案：
        接口挂了却提示「等级异常」，会把人引到完全错误的排查方向。
      -->
      <view v-else-if="viewState === 'error'" class="empty reveal">
        <Icon name="help" tone="muted" :size="56" />
        <text class="muted empty-text">折扣信息没加载出来</text>
        <text class="empty-hint">{{ errorHint }}</text>
        <UiButton variant="primary" class="empty-action" @tap="retry">重新加载</UiButton>
      </view>

      <view v-else-if="viewState === 'empty'" class="empty reveal">
        <Icon name="percent" tone="muted" :size="56" />
        <text class="muted empty-text">暂无等级折扣方案，请联系门店</text>
      </view>

      <template v-else>
        <!-- 当前等级：用该档卡面配色，与会员卡是同一套色 -->
        <view class="hero reveal" :class="`hero--${currentTheme}`">
          <view class="hero-top">
            <view class="hero-badge">
              <view class="hero-dot" />
              <text class="hero-level">{{ currentName }}</text>
            </view>
            <text class="hero-kicker">TODAY</text>
          </view>

          <view class="hero-figure">
            <text class="hero-rate num">{{ currentRateText }}</text>
            <view class="hero-meta">
              <text class="hero-scope">{{ currentScope }}</text>
              <text class="hero-spend">{{ currentSpend }}</text>
            </view>
          </view>

          <view v-if="nextCard" class="hero-progress">
            <view class="hero-progress-head">
              <text class="hero-progress-label">距 {{ nextCard.levelName }}</text>
              <text class="hero-progress-left">还差 {{ nextCard.growthLeft }} 成长值</text>
            </view>
            <view class="hero-track">
              <view class="hero-fill" :style="{ width: `${progress}%` }" />
            </view>
          </view>
          <view v-else-if="currentCard" class="hero-progress">
            <text class="hero-progress-label">已是最高等级，感谢一路同行</text>
          </view>
        </view>

        <!-- 等级方案：一档一卡，折扣幅度 / 适用范围 / 整周节奏 -->
        <view class="section-head reveal reveal-1">
          <text class="section-title head-title">等级与折扣</text>
          <text class="head-note">{{ cards.length }} 档 · 按成长值自动晋升</text>
        </view>

        <view
          v-for="card in cards"
          :key="card.levelKey"
          class="tier reveal"
          :class="[`tier--${card.theme}`, { 'tier--current': card.current, 'tier--locked': !card.reached }]"
        >
          <view class="tier-head">
            <view class="tier-name-wrap">
              <view class="tier-dot" />
              <text class="tier-name">{{ card.levelName }}</text>
            </view>
            <text class="tier-status">{{ card.statusText }}</text>
          </view>

          <view class="tier-body">
            <view class="tier-figure">
              <text class="tier-rate num">{{ card.rateText }}</text>
              <text class="tier-threshold">{{ card.minGrowth }} 成长值起</text>
            </view>
            <view class="tier-meta">
              <text class="tier-scope">{{ card.scopeText }}</text>
              <text class="tier-spend">{{ card.spendText }}</text>
            </view>
          </view>

          <!-- 一周七天：把「每周不同」摊开，顾客才知道哪天来最划算 -->
          <view class="week">
            <view
              v-for="day in card.week"
              :key="day.weekday"
              class="week-cell"
              :class="{ 'week-cell--today': day.today, 'week-cell--off': !day.active }"
            >
              <text class="week-day">{{ day.short }}</text>
              <text class="week-rate num">{{ day.text }}</text>
            </view>
          </view>
        </view>

        <view v-if="rules.length" class="rules reveal">
          <text class="rules-title">计算规则</text>
          <view v-for="(rule, index) in rules" :key="index" class="rule">
            <text class="rule-index num">{{ index + 1 }}</text>
            <text class="rule-text">{{ rule }}</text>
          </view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import Icon from '../../components/ui/Icon.vue'
import UiButton from '../../components/ui/Button.vue'
import { levelDiscount, loadLevelCatalog } from '../../store/levelDiscount'
import { isLoggedIn } from '../../store/auth'
import { openPage } from '../../utils/navigation'
import { themeOfLevel, rateText, shortRate } from '../../utils/levelTheme'

const loggedIn = computed(() => isLoggedIn.value)
const catalog = computed(() => levelDiscount.catalog)

/** 服务端数据 → 视图：换算与文案统一在这里做，模板里不出现乘法与字符串拼接 */
const cards = computed(() => {
  const levels = Array.isArray(catalog.value?.levels) ? catalog.value.levels : []
  return levels.map((level) => ({
    levelKey: level.levelKey,
    levelName: level.levelName,
    minGrowth: Number(level.minGrowth) || 0,
    current: level.current === true,
    reached: level.reached === true,
    theme: themeOfLevel(level.levelName),
    rateText: level.todayActive ? rateText(level.todayRate) : '原价',
    scopeText: level.todayActive ? (level.note || '全场商品') : '今日不参与折扣',
    spendText: Number(level.minSpend) > 0 ? `满 ¥${tidy(level.minSpend)} 可用` : '无门槛',
    statusText: level.current
      ? '当前等级'
      : level.reached
        ? '已达成'
        : `还差 ${Number(level.growthLeft) || 0} 成长值`,
    week: (Array.isArray(level.week) ? level.week : []).map((day) => ({
      weekday: day.weekday,
      short: String(day.label || '').replace('周', ''),
      today: day.today === true,
      active: day.active === true,
      text: day.active ? shortRate(day.rate) : '原'
    }))
  }))
})

const currentCard = computed(() => cards.value.find((card) => card.current) || null)
const nextCard = computed(() => {
  const index = cards.value.findIndex((card) => card.current)
  return index >= 0 && index < cards.value.length - 1 ? cards.value[index + 1] : null
})

const currentTheme = computed(() => currentCard.value?.theme || themeOfLevel(catalog.value?.currentLevel))
const currentName = computed(() => currentCard.value?.levelName || catalog.value?.currentLevel || '普通会员')
const currentRateText = computed(() => currentCard.value?.rateText || '原价')
const currentScope = computed(() => {
  if (!currentCard.value) return '当前等级不在成长值阶梯中，请联系门店'
  return currentCard.value.scopeText
})
const currentSpend = computed(() => currentCard.value?.spendText || '')

/**
 * 距下一档的进度。
 *
 * <p>百分比只用于画进度条，不参与任何权益判定 ——
 * 成长值与门槛都来自服务端，前端只做一次除法。
 */
const progress = computed(() => {
  if (!currentCard.value || !nextCard.value) return 100
  const from = currentCard.value.minGrowth
  const to = nextCard.value.minGrowth
  if (to <= from) return 100
  const value = Number(catalog.value?.growthValue) || 0
  return Math.min(100, Math.max(0, ((value - from) / (to - from)) * 100))
})

const rules = computed(() => (Array.isArray(catalog.value?.rules) ? catalog.value.rules : []))

/**
 * 页面状态：ready / loading / error / empty。
 *
 * <p>「有数据但 0 档」与「没数据」必须分开：前者是后端配置问题，
 * 后者是网络或接口问题，两者的处置方式完全不同。
 */
const viewState = computed(() => {
  if (catalog.value && cards.value.length) return 'ready'
  if (catalog.value) return 'empty'
  if (levelDiscount.catalogLoading) return 'loading'
  if (levelDiscount.catalogError) return 'error'
  return 'loading'
})

/** 失败原因原样透出：开发阶段能直接看出是 403 还是超时，顾客侧也不至于被技术术语砸脸 */
const errorHint = computed(() => {
  const reason = levelDiscount.catalogError
  return reason ? `原因：${reason}` : '请稍后重试'
})

function retry() {
  loadLevelCatalog(true)
}

/** 金额去掉无意义尾零：20.00 → 20 */
function tidy(value) {
  const num = Number(value)
  return Number.isFinite(num) ? String(Number(num.toFixed(2))) : '0'
}

function goLogin() {
  openPage('auth', { redirect: 'levelDiscount' })
}

onShow(() => {
  if (!isLoggedIn.value) return
  // 强制刷新：等级可能在别的端被调整，折扣也可能刚被运营改过
  loadLevelCatalog(true)
})
</script>

<style lang="scss" scoped>
.level-screen {
  min-height: 100vh;
}

.level-body {
  padding: $sp-6 $gutter $sp-8;
}

/* ---------------- 当前等级 ---------------- */
.hero {
  padding: $sp-6;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  color: $c-gold-on-dark;
  background: radial-gradient(circle at 90% 8%, $c-dark-glint 0, transparent 40%),
    linear-gradient(135deg, $c-dark 0%, $c-dark-2 58%, $c-dark-deep 100%);
  box-shadow: $sh-dark;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.hero-badge {
  display: flex;
  align-items: center;
  gap: $sp-1;
  min-width: 0;
  padding: $sp-0 $sp-3;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
}

.hero-dot {
  width: 10rpx;
  height: 10rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: currentColor;
}

.hero-level {
  font-size: $fs-micro;
  letter-spacing: $ls-normal;
  @include ellipsis;
}

.hero-kicker {
  flex-shrink: 0;
  @include caps;
  color: $c-on-dark-muted;
}

.hero-figure {
  display: flex;
  align-items: baseline;
  gap: $sp-5;
  margin-top: $sp-5;
}

/* 折扣是这张卡的主角：比会员卡上的积分再大一档，进页面第一眼就是它 */
.hero-rate {
  flex-shrink: 0;
  color: $c-gold-on-dark;
  font-size: $fs-display;
  font-weight: $fw-semibold;
  line-height: $lh-tight;
}

.hero-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: $sp-1;
}

.hero-scope {
  color: $c-on-dark;
  font-size: $fs-body-sm;
  @include ellipsis;
}

.hero-spend {
  color: $c-on-dark-muted;
  font-size: $fs-micro;
}

.hero-progress {
  margin-top: $sp-5;
  padding-top: $sp-4;
  border-top: 1rpx solid $c-hairline-gold;
}

.hero-progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
}

.hero-progress-label {
  color: $c-on-dark-muted;
  font-size: $fs-micro;
}

.hero-progress-left {
  color: $c-gold-on-dark;
  font-size: $fs-micro;
}

.hero-track {
  height: 8rpx;
  margin-top: $sp-2;
  overflow: hidden;
  border-radius: $r-pill;
  background: $c-dark-track;
}

.hero-fill {
  height: 100%;
  border-radius: inherit;
  background: $c-gold;
  transform-origin: left center;
  transition: width $dur-slow $ease-out;
}

/* ---------------- 区块标题 ---------------- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-7 2rpx $sp-3;
}

.head-title {
  margin-bottom: 0;
}

.head-note {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---------------- 等级卡 ---------------- */
.tier {
  position: relative;
  margin-bottom: $sp-3;
  padding: $sp-5 $sp-5 $sp-4;
  overflow: hidden;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
}

/* 左侧等级色条：颜色只在这一条与等级名上出现，浅底卡不会被色块压住 */
.tier::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 6rpx;
}

.tier--locked {
  opacity: 0.62;
}

.tier--current {
  border-color: $c-gold-line;
  box-shadow: $sh-2;
}

.tier-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.tier-name-wrap {
  display: flex;
  align-items: center;
  gap: $sp-1;
  min-width: 0;
}

.tier-dot {
  width: 10rpx;
  height: 10rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: currentColor;
}

.tier-name {
  font-size: $fs-body;
  font-weight: $fw-semibold;
  @include ellipsis;
}

.tier-status {
  flex-shrink: 0;
  color: $c-ink-muted;
  font-size: $fs-micro;
}

.tier--current .tier-status {
  color: $c-gold-text;
  font-weight: $fw-semibold;
}

.tier-body {
  display: flex;
  align-items: baseline;
  gap: $sp-5;
  margin-top: $sp-3;
}

.tier-figure {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: $sp-0;
}

.tier-rate {
  color: $c-ink-strong;
  font-size: $fs-h2;
  font-weight: $fw-semibold;
  line-height: $lh-tight;
}

.tier-threshold {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

.tier-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: $sp-0;
}

.tier-scope {
  color: $c-ink;
  font-size: $fs-caption;
  @include ellipsis;
}

.tier-spend {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---------------- 周历 ---------------- */
.week {
  display: flex;
  gap: $sp-1;
  margin-top: $sp-4;
  padding-top: $sp-3;
  border-top: 1rpx dashed $c-border;
}

.week-cell {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  padding: $sp-1 0;
  border-radius: $r-sm;
}

.week-day {
  color: $c-ink-faint;
  font-size: 20rpx;
}

.week-rate {
  color: $c-ink;
  font-size: $fs-micro;
  font-weight: $fw-semibold;
}

/* 今天：整格点亮，一眼看到「今天就是这个价」 */
.week-cell--today {
  background: $c-gold-soft;
}

.week-cell--today .week-day {
  color: $c-gold-text;
}

.week-cell--today .week-rate {
  color: $c-gold-text;
}

.week-cell--off .week-rate {
  color: $c-ink-faint;
  font-weight: $fw-regular;
}

/* 各档等级色：只作用于色条、等级名与圆点 */
@each $name, $palette in $level-cards {
  .tier--#{$name}::before {
    background: map-get($palette, ink);
  }

  .tier--#{$name} .tier-name,
  .tier--#{$name} .tier-dot {
    color: map-get($palette, ink);
  }

  .tier--#{$name} .tier-dot {
    background: map-get($palette, ink);
  }

  /* hero 用深底：换成正片卡面 */
  .hero--#{$name} {
    border-color: map-get($palette, line);
    color: map-get($palette, accent);
    background: radial-gradient(circle at 90% 8%, map-get($palette, glow) 0, transparent 40%),
      linear-gradient(135deg, map-get($palette, face-a) 0%, map-get($palette, face-b) 100%);
  }

  .hero--#{$name} .hero-level,
  .hero--#{$name} .hero-rate,
  .hero--#{$name} .hero-progress-left {
    color: map-get($palette, accent);
  }

  .hero--#{$name} .hero-badge {
    border-color: map-get($palette, line);
    color: map-get($palette, accent);
  }

  .hero--#{$name} .hero-kicker,
  .hero--#{$name} .hero-spend,
  .hero--#{$name} .hero-progress-label {
    color: map-get($palette, muted);
  }

  .hero--#{$name} .hero-progress {
    border-top-color: map-get($palette, line);
  }

  .hero--#{$name} .hero-fill {
    background: map-get($palette, accent);
  }
}

/* ---------------- 规则 ---------------- */
.rules {
  margin-top: $sp-6;
  padding: $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface-2;
}

.rules-title {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-caption;
  font-weight: $fw-semibold;
}

.rule {
  display: flex;
  align-items: flex-start;
  gap: $sp-2;
  margin-top: $sp-2;
}

.rule-index {
  flex-shrink: 0;
  color: $c-gold-text;
  font-size: $fs-micro;
  font-weight: $fw-semibold;
  line-height: $lh-body;
}

.rule-text {
  min-width: 0;
  flex: 1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
}

/* ---------------- 骨架 ---------------- */
.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
}

.skeleton-hero {
  height: 300rpx;
  border-radius: $r-lg;
}

.skeleton-row {
  height: 260rpx;
  border-radius: $r-lg;
}

/* ---------------- 空态 ---------------- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  padding: $sp-8 $sp-4;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-lg;
  background: $c-surface-2;
}

.empty-text {
  font-size: $fs-caption;
  text-align: center;
}

.empty-hint {
  padding: 0 $sp-4;
  color: $c-ink-faint;
  font-size: $fs-micro;
  line-height: $lh-body;
  text-align: center;
}

.empty-action {
  width: 100%;
  margin-top: $sp-2;
}

/* 窄屏：周历七格会挤，缩字号而不是换行（换行会破坏「一周一排」的读法） */
@media (max-width: 340px) {
  .level-body {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .week-day {
    font-size: 18rpx;
  }

  .week-rate {
    font-size: 20rpx;
  }
}
</style>
