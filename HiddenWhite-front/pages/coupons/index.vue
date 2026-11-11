<template>
  <view class="screen coupon-screen">
    <PhoneHeader title="优惠券" />

    <view class="coupon-body">
      <!-- 未登录：券要挂在会员身上，先引导登录 -->
      <view v-if="!loggedIn" class="empty reveal">
        <Icon name="ticket" tone="muted" :size="56" />
        <text class="muted empty-text">登录后即可领取每周好券</text>
        <UiButton variant="primary" class="empty-action" @tap="goLogin">去登录</UiButton>
      </view>

      <template v-else>
        <!--
          本周券面：与会员卡同一档深色表面。
          面额、门槛、有效期全部来自服务端配置，端上不写死任何金额 ——
          运营把面额从 6 调到 8，这里刷新一次就跟着变。
        -->
        <view class="weekly card-dark reveal">
          <view class="weekly-top">
            <text class="weekly-kicker">WEEKLY COUPON</text>
            <view :class="['weekly-badge', badgeClass]">
              <text class="weekly-badge-text">{{ stateLabel }}</text>
            </view>
          </view>

          <view class="weekly-face">
            <view class="weekly-amount">
              <text class="weekly-currency">¥</text>
              <text class="weekly-value num">{{ tidy(amount) }}</text>
            </view>
            <view class="weekly-meta">
              <text class="weekly-threshold">满 {{ tidy(minSpend) }} 元可用</text>
              <text class="weekly-valid">领取后 {{ validDays }} 天内有效</text>
            </view>
          </view>

          <text v-if="description" class="weekly-rule">{{ description }}</text>

          <view class="weekly-foot">
            <text class="weekly-reset">{{ resetHint }}</text>
          </view>

          <view class="weekly-action">
            <UiButton variant="primary" :disabled="!canClaim" @tap="claim" style="width: 100%;">
              {{ claimLabel }}
            </UiButton>
          </view>
        </view>

        <!-- 我的券 -->
        <view class="section-head reveal reveal-1">
          <text class="section-title head-title">我的券</text>
          <text class="head-note">{{ countLabel }}</text>
        </view>

        <view v-if="loading && !rows.length" class="skeleton-list">
          <view class="skeleton skeleton-row" />
          <view class="skeleton skeleton-row" />
        </view>

        <view v-else-if="!rows.length" class="empty reveal reveal-1">
          <Icon name="ticket" tone="muted" :size="56" />
          <text class="muted empty-text">还没有优惠券，每周一记得来领一张</text>
        </view>

        <view
          v-for="row in rows"
          :key="row.id"
          :class="['ticket-row', { 'ticket-row--dead': row.status !== 'UNUSED' }]"
        >
          <!-- 票根：面额是这张卡唯一需要被一眼看到的信息 -->
          <view class="ticket-stub">
            <text class="ticket-amount num">
              <text class="ticket-currency">¥</text>{{ tidy(row.amount) }}
            </text>
            <text class="ticket-threshold">满 {{ tidy(row.minSpend) }} 可用</text>
          </view>

          <view class="ticket-main">
            <view class="ticket-head">
              <text class="ticket-title">{{ row.title }}</text>
              <text class="ticket-state">{{ row.statusLabel }}</text>
            </view>
            <text class="ticket-dates">
              {{ row.status === 'UNUSED' ? `有效期至 ${row.expiresAt}` : row.reason }}
            </text>
            <text v-if="row.expiringSoon" class="ticket-warn">不足 1 天到期，尽快使用</text>
          </view>
        </view>

        <view v-if="rows.length" class="footnote">
          每周一 00:00 重置，每位会员每周可领一张；单笔订单限用一张，与首单礼遇不叠加。
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
import { coupons, claimCoupon, loadCoupons } from '../../store/coupons'
import { isLoggedIn } from '../../store/auth'
import { openPage } from '../../utils/navigation'
import { showSuccess, showError } from '../../utils/toast'

const loggedIn = computed(() => isLoggedIn.value)
const wallet = computed(() => coupons.data)
const loading = computed(() => coupons.loading)

const amount = computed(() => wallet.value?.amount ?? 0)
const minSpend = computed(() => wallet.value?.minSpend ?? 0)
const validDays = computed(() => wallet.value?.validDays ?? 0)
const description = computed(() => wallet.value?.description || '')
const stateLabel = computed(() => wallet.value?.weekStateLabel || '')
const resetHint = computed(() => wallet.value?.resetHint || '')
const rows = computed(() => wallet.value?.coupons || [])

const claimable = computed(() => wallet.value?.weekState === 'CLAIMABLE')
const canClaim = computed(() => claimable.value && !coupons.claiming)

/** 按钮文案：五种状态各说一句人话，不要统一成「不可领取」 */
const claimLabel = computed(() => {
  if (wallet.value?.weekState === 'CLAIMABLE') return '领取本周好券'
  return stateLabel.value || '暂不可领取'
})

/** 角标配色：可领用金色强调，其余退回中性，整屏只允许一个强调位 */
const badgeClass = computed(() => (claimable.value ? 'weekly-badge--active' : ''))

const countLabel = computed(() => {
  const unused = wallet.value?.unusedCount ?? 0
  if (!rows.value.length) return '每周一张'
  return unused > 0 ? `${unused} 张可用` : `共 ${rows.value.length} 张`
})

/** 金额去掉无意义的尾零：6.00 → 6，12.50 → 12.5 */
function tidy(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0'
  return String(Number(num.toFixed(2)))
}

async function claim() {
  if (!canClaim.value) return
  try {
    const result = await claimCoupon()
    if (result?.weekState === 'CLAIMED') {
      // 有效期由服务端算好（领取时刻 + validDays），这里只回述它给的那张券
      const mine = result.coupons?.find((row) => row.status === 'UNUSED')
      showSuccess(mine ? `已放入券包，${mine.expiresAt} 前有效` : '已放入券包')
    } else {
      showSuccess('已领取')
    }
  } catch (error) {
    // 已领完 / 持有超限 / 未开放：原文案已经是给人看的一句话，直接透出
    showError(error.message || '领取失败，请稍后再试')
  }
}

function goLogin() {
  openPage('auth', { redirect: 'coupons' })
}

onShow(() => {
  if (!isLoggedIn.value) return
  // 每次进页面都强制刷新：可能在别的设备上领过、或券刚被订单用掉
  loadCoupons(true)
})
</script>

<style lang="scss" scoped>
.coupon-screen {
  min-height: 100vh;
}

.coupon-body {
  padding: $sp-6 $gutter $sp-8;
}

/* ---- 本周券面 ---- */
.weekly {
  padding: $sp-6;
}

.weekly-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.weekly-kicker {
  @include caps;
  color: $c-on-dark-muted;
}

/* 状态角标：金底深字（可领）或半透明深底浅字（其余），一眼区分亮与灰 */
.weekly-badge {
  flex-shrink: 0;
  padding: 2rpx $sp-3;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
}

.weekly-badge--active {
  border-color: transparent;
  background: $c-gold;
}

.weekly-badge-text {
  color: $c-gold-on-dark;
  font-size: $fs-micro;
  letter-spacing: $ls-normal;
  white-space: nowrap;
}

.weekly-badge--active .weekly-badge-text {
  color: $c-dark;
  font-weight: $fw-semibold;
}

.weekly-face {
  display: flex;
  align-items: baseline;
  gap: $sp-5;
  margin-top: $sp-5;
}

.weekly-amount {
  display: flex;
  align-items: baseline;
  flex-shrink: 0;
}

.weekly-currency {
  color: $c-gold-on-dark;
  font-size: $fs-title-lg;
  font-weight: $fw-semibold;
}

/* 面额是整张卡的主角：显示级字号，与积分卡的数字同一档 */
.weekly-value {
  color: $c-gold-on-dark;
  font-size: $fs-display;
  font-weight: $fw-semibold;
  line-height: $lh-tight;
}

.weekly-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: $sp-1;
}

.weekly-threshold {
  color: $c-on-dark;
  font-size: $fs-body-sm;
}

.weekly-valid {
  color: $c-on-dark-muted;
  font-size: $fs-micro;
}

.weekly-rule {
  display: block;
  margin-top: $sp-5;
  color: $c-on-dark-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
}

.weekly-foot {
  margin-top: $sp-4;
  padding-top: $sp-4;
  border-top: 1rpx solid $c-hairline-gold;
}

.weekly-reset {
  color: $c-on-dark-muted;
  font-size: $fs-micro;
}

.weekly-action {
  margin-top: $sp-4;
}

/* ---- 区块标题 ---- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-6 2rpx $sp-3;
}

.head-title {
  margin-bottom: 0;
}

.head-note {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---- 券卡：左票根 + 右详情，中间虚线撕口 ---- */
.ticket-row {
  display: flex;
  align-items: stretch;
  margin-bottom: $sp-2;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
  overflow: hidden;
}

.ticket-stub {
  display: flex;
  width: 176rpx;
  flex-shrink: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-1;
  padding: $sp-5 $sp-2;
  background: $c-gold-soft;
  /* 票根的撕口：虚线画在右边缘内侧，不额外占宽度 */
  border-right: 1rpx dashed $c-gold-line;
}

.ticket-amount {
  color: $c-gold-text;
  font-size: $fs-h2;
  font-weight: $fw-semibold;
  line-height: $lh-tight;
}

.ticket-currency {
  font-size: $fs-body-sm;
  font-weight: $fw-semibold;
}

.ticket-threshold {
  color: $c-ink-muted;
  font-size: $fs-micro;
  white-space: nowrap;
}

.ticket-main {
  min-width: 0;
  flex: 1;
  padding: $sp-4 $sp-5;
}

.ticket-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-2;
}

.ticket-title {
  min-width: 0;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: $fw-semibold;
  @include ellipsis;
}

.ticket-state {
  flex-shrink: 0;
  color: $c-ink-muted;
  font-size: $fs-micro;
}

.ticket-dates {
  display: block;
  margin-top: $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
}

/* 即将过期的提醒用文字 + 金褐色，不用红色：过期不是错误，是时间提醒 */
.ticket-warn {
  display: block;
  margin-top: $sp-1;
  color: $c-gold-text;
  font-size: $fs-micro;
}

/* 已使用 / 已过期：整张卡降饱和，票根退回中性底，一眼区分「还能用的」 */
.ticket-row--dead {
  background: $c-surface-2;
  box-shadow: none;
}

.ticket-row--dead .ticket-stub {
  background: $c-surface-3;
  border-right-color: $c-border;
}

.ticket-row--dead .ticket-amount {
  color: $c-ink-faint;
}

.ticket-row--dead .ticket-title {
  color: $c-ink-muted;
  font-weight: $fw-medium;
}

/* ---- 空态 / 骨架 ---- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  padding: $sp-7 $sp-4;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-lg;
  background: $c-surface-2;
}

.empty-text {
  font-size: $fs-caption;
  text-align: center;
}

.empty-action {
  width: 100%;
  margin-top: $sp-2;
}

.skeleton-row {
  height: 148rpx;
  margin-bottom: $sp-2;
  border-radius: $r-lg;
}

.footnote {
  padding: $sp-5 0 $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
  line-height: $lh-body;
}

@media (max-width: 340px) {
  .coupon-body {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .ticket-stub {
    width: 148rpx;
  }
}
</style>
