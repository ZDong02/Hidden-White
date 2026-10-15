<template>
  <!-- 会员面板：会员卡、积分概览与专属礼遇 -->
  <scroll-view class="panel-scroll member-panel" scroll-y>
    <view class="member-card">
      <view class="card-top">
        <view>
          <text class="brand">隐白</text>
          <text class="brand-caption">HIDDEN WHITE · MEMBER</text>
        </view>
        <text class="level-badge">✦ {{ member.memberLevel || '普通会员' }}</text>
      </view>

      <view class="card-bottom">
        <view>
          <text class="card-label">会员卡号</text>
          <text class="card-number">{{ member.cardNumber || '游客体验卡' }}</text>
        </view>
        <view class="points-block">
          <text class="card-label">当前积分</text>
          <text class="points">{{ member.points ?? 0 }}</text>
        </view>
      </view>
    </view>

    <view class="summary-card">
      <view class="summary-item" @tap="showFeature('积分明细')">
        <text class="summary-value">{{ member.points ?? 0 }}</text>
        <text class="summary-label">积分明细</text>
      </view>
      <view class="summary-divider" />
      <view class="summary-item" @tap="showFeature('我的优惠券')">
        <text class="summary-value">{{ member.coupons ?? 0 }}</text>
        <text class="summary-label">优惠券</text>
      </view>
      <view class="summary-divider" />
      <view class="summary-item growth-item">
        <text class="summary-value">{{ member.growthValue ?? 0 }}</text>
        <text class="summary-label">成长值</text>
        <view class="growth-track"><view class="growth-progress" /></view>
      </view>
    </view>

    <view class="section-heading">
      <text>会员专属礼遇</text>
      <text class="section-note">为日常添一份特别</text>
    </view>

    <view class="benefits-grid">
      <view
        v-for="benefit in benefits"
        :key="benefit.title"
        class="benefit-card"
        @tap="showFeature(benefit.title)"
      >
        <text class="benefit-icon">{{ benefit.icon }}</text>
        <text class="benefit-title">{{ benefit.title }}</text>
        <text class="benefit-description">{{ benefit.description }}</text>
      </view>
    </view>

    <view class="activity-card" @tap="showFeature('会员专享活动')">
      <view class="activity-copy">
        <text class="activity-kicker">MEMBER EXCLUSIVE</text>
        <text class="activity-title">每一份热爱，都值得回馈</text>
        <text class="activity-description">查看本月会员专属活动与限定礼遇</text>
        <text class="activity-link">了解更多 ↗</text>
      </view>
      <text class="activity-mark">✦</text>
    </view>

    <view class="member-footnote">会员权益以门店实际活动为准</view>
  </scroll-view>
</template>

<script setup>
import { ref } from 'vue'
import { currentUser, isLoggedIn } from '../../store/auth'
import { BACKEND_ENABLED, requestApi } from '../../config/backend'

const defaultBenefits = [
  { icon: '◇', title: '积分兑换', description: '好礼随心兑' },
  { icon: '✧', title: '生日礼遇', description: '生日专属惊喜' },
  { icon: '％', title: '会员折扣', description: '指定商品专享' },
  { icon: '＋', title: '免费升杯', description: '每月专属礼遇' }
]
const benefits = ref(defaultBenefits)
const member = ref({})

// 壳页面在切到本面板 / 页面 onShow 时调用
async function activate() {
  if (!isLoggedIn.value) {
    member.value = { memberLevel: '游客', cardNumber: '登录后查看', points: 0, coupons: 0, growthValue: 0 }
    return
  }

  if (!BACKEND_ENABLED) {
    member.value = { displayName: currentUser.value.displayName, memberLevel: currentUser.value.level, cardNumber: '本地体验卡' }
    return
  }
  try {
    const [profile, rows] = await Promise.all([
      requestApi(`/members/${encodeURIComponent(currentUser.value.id)}`),
      requestApi('/members/benefits')
    ])
    member.value = { ...profile, memberLevel: profile.memberLevel || profile.level }
    benefits.value = rows
  } catch (error) {
    uni.showToast({ title: error.message || '会员信息加载失败', icon: 'none' })
  }
}

function showFeature(title) {
  uni.showModal({
    title,
    content: '该会员服务当前仅展示权益说明，积分兑换与优惠券核销尚未开放。',
    showCancel: false,
    confirmText: '知道了'
  })
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
/* 与 Menu/Orders 面板统一横向留白（原为 36rpx，四个面板不一致） */
.member-panel {
  /* 顶部留白与首页面板一致（$sp-6） */
  padding: $sp-6 $gutter 218rpx;
  box-sizing: border-box;
}

/* 小程序端：与首页一致，整体下移避让右上角胶囊按钮 */
/* #ifdef MP-WEIXIN */
.member-panel {
  padding-top: 116rpx;
}
/* #endif */

.member-card {
  position: relative;
  min-height: 330rpx;
  overflow: hidden;
  padding: $sp-6;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  color: $c-gold-on-dark;
  background: radial-gradient(circle at 90% 10%, #3c3421 0, transparent 38%),
    linear-gradient(135deg, #10100f 0%, #24221b 58%, #0c0c0b 100%);
  box-shadow: $sh-dark;
}

.member-card::after {
  position: absolute;
  right: -82rpx;
  bottom: -160rpx;
  width: 350rpx;
  height: 350rpx;
  border: 1rpx solid rgba(212, 173, 47, 0.25);
  border-radius: 50%;
  content: '';
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

.card-top > view,
.card-bottom > view {
  min-width: 0;
}

.brand {
  display: block;
  color: $c-gold-on-dark;
  font-family: $font-display;
  font-size: 44rpx;
  letter-spacing: $ls-wider;
}

.brand-caption,
.card-label {
  display: block;
  /* 17rpx（8.5px）在手机上几乎读不清，统一抬到 22rpx */
  color: $c-on-dark-muted;
  font-size: $fs-micro;
  letter-spacing: $ls-wide;
}

.brand-caption {
  margin-top: $sp-1;
  text-transform: uppercase;
}

.level-badge {
  flex-shrink: 0;
  max-width: 48%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: $sp-1 $sp-3;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  color: $c-gold-on-dark;
  font-size: $fs-micro;
}

.card-bottom {
  align-items: flex-end;
  margin-top: $sp-8;
}

.card-number {
  display: block;
  margin-top: $sp-2;
  color: #e2cf94;
  font-size: 28rpx;
  letter-spacing: $ls-wide;
  white-space: nowrap;
}

.points-block {
  text-align: right;
}

.points {
  display: block;
  margin-top: 4rpx;
  color: $c-gold-on-dark;
  font-size: 46rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.summary-card {
  display: flex;
  align-items: stretch;
  margin-top: $sp-4;
  padding: $sp-2 $sp-1;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
}

/* 三个数据格子都是可点入口，高度对齐 $tap-min（44px） */
.summary-item {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: $tap-min;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-1;
  border-radius: $r-sm;
  transition: background-color $dur-fast ease;
}

.summary-value {
  color: $c-ink-strong;
  font-size: $fs-title;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.summary-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: $c-ink-muted;
  font-size: 22rpx;
}

.summary-divider {
  width: 1rpx;
  height: 58rpx;
  align-self: center;
  background: $c-divider;
}

.growth-track {
  width: 104rpx;
  max-width: 100%;
  height: 6rpx;
  margin-top: 2rpx;
  overflow: hidden;
  border-radius: $r-pill;
  background: $c-surface-3;
}

.growth-progress {
  width: 64%;
  height: 100%;
  border-radius: inherit;
  background: $c-gold;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-6 2rpx $sp-4;
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.section-note {
  color: $c-ink-muted;
  font-size: 22rpx;
  font-weight: 400;
}

.benefits-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.benefit-card {
  display: flex;
  width: calc(50% - #{$sp-2});
  min-width: 0;
  min-height: 168rpx;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: $sp-4 $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  box-sizing: border-box;
  margin-bottom: $sp-3;
  transition: transform $dur-fast ease, box-shadow 200ms ease, border-color 200ms ease;
}

.benefit-icon {
  color: $c-gold-display;
  font-size: 34rpx;
  line-height: 1.1;
}

.benefit-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: $sp-2;
  color: $c-ink-strong;
  font-size: 26rpx;
  font-weight: 600;
}

.benefit-description {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: $sp-1;
  color: $c-ink-muted;
  font-size: 22rpx;
}

.activity-card {
  position: relative;
  display: flex;
  min-height: 190rpx;
  align-items: center;
  justify-content: space-between;
  margin-top: $sp-4;
  overflow: hidden;
  padding: $sp-5 $sp-5;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-md;
  color: #f0e6c8;
  background: linear-gradient(110deg, #11110f 0%, #252218 100%);
}

.activity-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  max-width: calc(100% - 70rpx);
}

.activity-kicker {
  display: block;
  color: #cdaa53;
  font-size: 20rpx;
  letter-spacing: $ls-wide;
}

.activity-title {
  display: block;
  margin-top: $sp-2;
  font-family: $font-display;
  font-size: 28rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-description {
  display: block;
  margin-top: $sp-1;
  color: #b0aca1;
  font-size: 22rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-link {
  display: block;
  margin-top: $sp-2;
  color: $c-gold-on-dark;
  font-size: 22rpx;
}

.activity-mark {
  position: absolute;
  right: 27rpx;
  color: rgba(212, 173, 47, 0.22);
  font-size: 130rpx;
}

.member-footnote {
  padding: $sp-4 0 $sp-1;
  color: $c-ink-muted;
  font-size: 22rpx;
  text-align: center;
}

/* ---- 交互态 ---- */
/* #ifdef H5 */
.benefit-card:hover {
  transform: translateY(-4rpx);
  box-shadow: $sh-2;
  border-color: $c-border-strong;
}

.summary-item:hover {
  background: $c-surface-2;
}
/* #endif */

.benefit-card:active {
  transform: scale(0.98);
}

@media (max-width: 340px) {
  .member-panel {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .benefit-card {
    width: 100%;
  }

  .card-number {
    font-size: 24rpx;
    letter-spacing: 1rpx;
  }

  .section-heading {
    flex-wrap: wrap;
    gap: $sp-1;
  }
}
</style>
