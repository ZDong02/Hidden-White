<template>
  <!-- 会员面板：会员卡、积分概览与专属礼遇 -->
  <scroll-view class="panel-scroll member-panel" scroll-y>
    <view class="member-card reveal">
      <view class="card-top">
        <view class="card-block">
          <text class="brand">隐白</text>
          <text class="brand-caption">HIDDEN WHITE · MEMBER</text>
        </view>
        <view class="level-badge">
          <Icon name="sparkle" tone="gold-on-dark" :size="22" />
          <text class="level-text">{{ member.memberLevel || '普通会员' }}</text>
        </view>
      </view>

      <view class="card-bottom">
        <view class="card-block">
          <text class="card-label">会员卡号</text>
          <text class="card-number">{{ member.cardNumber || '游客体验卡' }}</text>
        </view>
        <view class="points-block">
          <text class="card-label">当前积分</text>
          <text class="points num">{{ member.points ?? 0 }}</text>
          <view
            class="points-action"
            hover-class="is-press"
            :hover-stay-time="60"
            @tap.stop="openRedeem"
          >
            <text class="points-action-text">去兑换</text>
            <Icon name="chevron-right" tone="gold-on-dark" :size="20" />
          </view>
        </view>
      </view>
    </view>

    <view class="stat-grid reveal reveal-1">
      <view class="stat-cell" hover-class="stat-cell--press" :hover-stay-time="60" @tap="openPointsLog">
        <text class="stat-value">{{ member.points ?? 0 }}</text>
        <text class="stat-label">积分明细</text>
      </view>
      <view class="stat-sep" />
      <view class="stat-cell" hover-class="stat-cell--press" :hover-stay-time="60" @tap="showFeature('我的优惠券')">
        <text class="stat-value">{{ member.coupons ?? 0 }}</text>
        <text class="stat-label">优惠券</text>
      </view>
      <view class="stat-sep" />
      <view class="stat-cell">
        <text class="stat-value">{{ member.growthValue ?? 0 }}</text>
        <text class="stat-label">成长值</text>
        <view class="growth-track"><view class="growth-progress" /></view>
      </view>
    </view>

    <view class="section-head reveal reveal-2">
      <text class="heading-main">会员专属礼遇</text>
      <text class="heading-note">为日常添一份特别</text>
    </view>

    <view class="benefits-grid">
      <view
        v-for="benefit in benefits"
        :key="benefit.title"
        class="benefit-card"
        hover-class="benefit-card--press"
        :hover-stay-time="60"
        @tap="openBenefit(benefit)"
      >
        <view class="benefit-icon">
          <Icon :name="benefit.icon" tone="gold" :size="32" />
        </view>
        <text class="benefit-title">{{ benefit.title }}</text>
        <text class="benefit-description">{{ benefit.description }}</text>
      </view>
    </view>

    <view class="activity-card card-dark reveal reveal-3" hover-class="is-press" :hover-stay-time="60" @tap="showFeature('会员专享活动')">
      <view class="activity-copy">
        <text class="activity-kicker">MEMBER EXCLUSIVE</text>
        <text class="activity-title">每一份热爱，都值得回馈</text>
        <text class="activity-description">查看本月会员专属活动与限定礼遇</text>
        <view class="activity-link">
          <text>了解更多</text>
          <Icon name="arrow-up-right" tone="gold-on-dark" :size="22" />
        </view>
      </view>
      <view class="activity-mark">
        <Icon name="sparkle" tone="decor" :size="120" />
      </view>
    </view>

    <view class="member-footnote">会员权益以门店实际活动为准</view>
  </scroll-view>
</template>

<script setup>
import { ref } from 'vue'
import Icon from '../ui/Icon.vue'
import { currentUser, isLoggedIn } from '../../store/auth'
import { requestApi } from '../../config/backend'
import { showNotice } from '../../utils/toast'
import { openPage } from '../../utils/navigation'

// 礼遇图标走统一图标集：名称对应 scripts/icons 下的母版
const defaultBenefits = [
  { icon: 'gift', title: '积分兑换', description: '好礼随心兑' },
  { icon: 'calendar', title: '生日礼遇', description: '生日专属惊喜' },
  { icon: 'percent', title: '会员折扣', description: '指定商品专享' },
  { icon: 'cup', title: '免费升杯', description: '每月专属礼遇' }
]
const benefits = ref(defaultBenefits)
const member = ref({})

// 壳页面在切到本面板 / 页面 onShow 时调用
async function activate() {
  if (!isLoggedIn.value) {
    member.value = { memberLevel: '游客', cardNumber: '登录后查看', points: 0, coupons: 0, growthValue: 0 }
    return
  }

  try {
    const [profile, rows] = await Promise.all([
      requestApi(`/members/${encodeURIComponent(currentUser.value.id)}`),
      requestApi('/members/benefits')
    ])
    member.value = { ...profile, memberLevel: profile.memberLevel || profile.level }
    // 后端若返回了图标字段，只接受已登记的图标名，否则保留本地图标
    benefits.value = rows.map((row, index) => ({
      ...row,
      icon: defaultBenefits[index % defaultBenefits.length].icon
    }))
  } catch (error) {
    uni.showToast({ title: error.message || '会员信息加载失败', icon: 'none' })
  }
}

function showFeature(title) {
  showNotice(title, '该会员服务当前仅展示权益说明，优惠券核销尚未开放。')
}

/**
 * 积分兑换入口：会员卡「去兑换」与礼遇卡「积分兑换」共用。
 * 未登录先去登录页，登录成功由登录页按 redirect=redeem 直接换到兑换页。
 */
function openRedeem() {
  if (!isLoggedIn.value) {
    openPage('auth', { redirect: 'redeem' })
    return
  }
  openPage('redeem')
}

/** 积分明细：只读流水，游客与未登录同样先引导登录。 */
function openPointsLog() {
  if (!isLoggedIn.value) {
    openPage('auth', { redirect: 'pointsLog' })
    return
  }
  openPage('pointsLog')
}

/**
 * 礼遇卡点击：只有「积分兑换」这一张对应真实功能，其余仍是权益说明。
 * 用标题前缀匹配而不是下标，是因为礼遇列表来自后端（管理端可增删改）。
 */
function openBenefit(benefit) {
  if (String(benefit?.title || '').includes('积分')) {
    openRedeem()
    return
  }
  showFeature(benefit.title)
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
.member-panel {
  padding: $pad-panel-top $gutter $pad-panel-plain;
  box-sizing: border-box;
}

/* 小程序端：与首页一致，整体下移避让右上角胶囊按钮 */
/* #ifdef MP-WEIXIN */
.member-panel {
  padding-top: $pad-capsule;
}
/* #endif */

/* ---- 会员卡：全站最深的一档表面，用来承载「身份」 ---- */
.member-card {
  position: relative;
  min-height: 330rpx;
  overflow: hidden;
  padding: $sp-6;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  color: $c-gold-on-dark;
  background: radial-gradient(circle at 90% 10%, $c-dark-glint 0, transparent 38%),
    linear-gradient(135deg, $c-dark 0%, $c-dark-2 58%, $c-dark-deep 100%);
  box-shadow: $sh-dark;
}

/* 右下角金色细环：与 Banner / 进度卡同一笔触 */
.member-card::after {
  content: '';
  position: absolute;
  right: -82rpx;
  bottom: -160rpx;
  width: 350rpx;
  height: 350rpx;
  border: 1rpx solid $c-hairline-gold;
  border-radius: 50%;
  z-index: 0;
}

.card-top,
.card-bottom {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.card-block,
.points-block {
  min-width: 0;
}

.brand {
  display: block;
  color: $c-gold-on-dark;
  font-family: $font-display;
  font-size: $fs-h2;
  letter-spacing: $ls-wider;
}

.brand-caption,
.card-label {
  display: block;
  @include caps;
  color: $c-on-dark-muted;
}

.brand-caption {
  margin-top: $sp-1;
}

/* 等级徽标：图标 + 文字，图标尺寸跟着字号走 */
.level-badge {
  display: flex;
  align-items: center;
  gap: $sp-1;
  flex-shrink: 0;
  max-width: 46%;
  padding: $sp-0 $sp-3;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  color: $c-gold-on-dark;
}

.level-text {
  font-size: $fs-micro;
  letter-spacing: $ls-normal;
  @include ellipsis;
}

.card-bottom {
  align-items: flex-end;
  margin-top: $sp-8;
}

.card-number {
  display: block;
  margin-top: $sp-2;
  color: $c-gold-on-dark;
  font-family: $font-number;
  font-size: $fs-body-sm;
  letter-spacing: $ls-wide;
  white-space: nowrap;
}

.points-block {
  text-align: right;
}

.points {
  display: block;
  margin-top: $sp-0;
  color: $c-gold-on-dark;
  font-size: $fs-h2;
  font-weight: 600;
}

/* 「去兑换」：深色卡上的次级入口，用金色文字 + 20rpx 图标，不抢积分数字的视觉重量 */
.points-action {
  display: inline-flex;
  align-items: center;
  gap: $sp-0;
  margin-top: $sp-1;
  padding: $sp-0 $sp-2;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
}

.points-action-text {
  color: $c-gold-on-dark;
  font-size: $fs-micro;
  letter-spacing: $ls-normal;
}

/* ---- 概览数据 ---- */
.stat-grid {
  margin-top: $sp-4;
}

.stat-cell--press {
  background: $c-surface-2;
}

.growth-track {
  width: 104rpx;
  max-width: 100%;
  height: 8rpx;
  margin-top: $sp-1;
  overflow: hidden;
  border-radius: $r-pill;
  background: $c-border-strong;
}

.growth-progress {
  width: 64%;
  height: 100%;
  border-radius: inherit;
  background: $c-gold;
  transform-origin: left center;
  animation: track-grow 720ms $ease-out 320ms both;
}

@keyframes track-grow {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

/* ---- 区块标题 ---- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-6 2rpx $sp-4;
}

.heading-main {
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
  letter-spacing: $ls-normal;
}

.heading-note {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---- 礼遇卡 ---- */
.benefits-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.benefit-card {
  display: flex;
  width: calc(50% - #{$sp-2});
  min-width: 0;
  min-height: 176rpx;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: $sp-4 $sp-5;
  margin-bottom: $sp-3;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out,
    border-color $dur-fast ease;
}

.benefit-card--press {
  transform: scale(0.977);
}

/* 图标底：44rpx 的浅金方块，四个礼遇卡的图标视觉重量因此完全一致 */
.benefit-icon {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: center;
  border-radius: $r-xs;
  background: $c-gold-soft;
}

.benefit-title {
  max-width: 100%;
  margin-top: $sp-2;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: 600;
  @include ellipsis;
}

.benefit-description {
  max-width: 100%;
  margin-top: $sp-0;
  color: $c-ink-muted;
  font-size: $fs-micro;
  @include ellipsis;
}

/* ---- 活动卡 ---- */
.activity-card {
  display: flex;
  min-height: 196rpx;
  align-items: center;
  justify-content: space-between;
  margin-top: $sp-2;
  padding: $sp-5;
}

.activity-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  max-width: calc(100% - 96rpx);
}

.activity-kicker {
  display: block;
  @include caps;
  color: $c-gold-on-dark;
}

.activity-title {
  display: block;
  margin-top: $sp-2;
  font-family: $font-display;
  font-size: $fs-title-sm;
  color: $c-on-dark;
  @include ellipsis;
}

.activity-description {
  display: block;
  margin-top: $sp-1;
  color: $c-on-dark-muted;
  font-size: $fs-micro;
  @include ellipsis;
}

.activity-link {
  display: flex;
  align-items: center;
  gap: $sp-0;
  margin-top: $sp-2;
  color: $c-gold-on-dark;
  font-size: $fs-micro;
}

/* 底纹星标：只做氛围，压在右下角且半透明，读屏应忽略 */
.activity-mark {
  position: absolute;
  right: 20rpx;
  bottom: -18rpx;
  opacity: 0.28;
  pointer-events: none;
}

.member-footnote {
  padding: $sp-4 0 $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
}

/* #ifdef H5 */
.benefit-card:hover {
  transform: translateY(-4rpx);
  box-shadow: $sh-2;
  border-color: $c-border-strong;
}

.activity-card:hover {
  box-shadow: $sh-3;
}
/* #endif */

@media (max-width: 340px) {
  .member-panel {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .benefit-card {
    width: 100%;
  }

  .card-number {
    font-size: $fs-caption;
    letter-spacing: 1rpx;
  }

  .section-head {
    flex-wrap: wrap;
    gap: $sp-1;
  }
}
</style>
