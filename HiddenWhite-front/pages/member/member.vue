<template>
  <view class="screen member-screen">
    <PhoneHeader title="会员中心" class="screen-header" />

    <scroll-view class="member-content" scroll-y>
      <!-- 3D 翻转会员卡：正面 = 卡号 / 等级 / 积分，背面 = 积分规则与使用条款 -->
      <!-- 点击沿 Y 轴翻转，旋转至 90° 侧立的瞬间切换正反面内容，再次点击翻回正面 -->
      <view
        class="member-card"
        aria-role="button"
        hover-class="member-card--pressed"
        :hover-stay-time="120"
        @tap="flipCard"
      >
        <view class="card-rotor" :class="{ 'is-flipped': isCardFlipped }">
          <!-- 正面：核心信息 -->
          <view v-show="!showCardBack" class="card-face card-face--front">
            <view class="card-top">
              <view class="card-block">
                <text class="brand">隐白</text>
                <text class="brand-caption">HIDDEN WHITE · MEMBER</text>
              </view>
              <text class="level-badge">✦ {{ member.memberLevel || '普通会员' }}</text>
            </view>

            <view class="card-middle">
              <view class="card-chip" />
              <text class="flip-hint">点击翻转 ↻</text>
            </view>

            <view class="card-bottom">
              <view class="card-block">
                <text class="card-label">会员卡号</text>
                <text class="card-number">{{ member.cardNumber || '游客体验卡' }}</text>
              </view>
              <view class="points-block">
                <text class="card-label">当前积分</text>
                <text class="points">{{ member.points ?? 0 }}</text>
              </view>
            </view>
          </view>

          <!-- 背面：规则说明 -->
          <view v-show="showCardBack" class="card-face card-face--back">
            <view class="rules-header">
              <text class="rules-title">会员规则</text>
              <text class="rules-caption">MEMBER TERMS</text>
            </view>
            <view class="rules-list">
              <view v-for="rule in cardRules" :key="rule.name" class="rule-item">
                <text class="rule-dot">◆</text>
                <text class="rule-name">{{ rule.name }}</text>
                <text class="rule-detail">{{ rule.detail }}</text>
              </view>
            </view>
            <view class="flip-back-hint">
              <text>再次轻触返回正面</text>
              <text class="hint-icon">↻</text>
            </view>
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

    <view class="bottom-fixed member-footer">
      <MainTabBar active="member" @select="handleTabSelect" />
      <HomeIndicator />
    </view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { onUnmounted, ref } from 'vue'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import MainTabBar from '../../components/MainTabBar.vue'
import { shop } from '../../store/index'
import { currentUser, isLoggedIn } from '../../store/auth'
import { resetToPage } from '../../utils/navigation'
import { BACKEND_ENABLED, requestApi } from '../../config/backend'

const defaultBenefits = [
  { icon: '◇', title: '积分兑换', description: '好礼随心兑' },
  { icon: '✧', title: '生日礼遇', description: '生日专属惊喜' },
  { icon: '％', title: '会员折扣', description: '指定商品专享' },
  { icon: '＋', title: '免费升杯', description: '每月专属礼遇' }
]
const benefits = ref(defaultBenefits)
const member = ref({})

// ===== 3D 翻转会员卡 =====
const CARD_FLIP_DURATION = 700 // 与样式 .card-rotor 的 transition 时长保持一致
const isCardFlipped = ref(false) // 翻转终态：true = 停在背面
const showCardBack = ref(false) // 当前渲染哪一面（翻转中途切换）
const isCardAnimating = ref(false) // 动画锁：翻转过程中忽略再次点击
const cardRules = [
  { name: '积分累计', detail: '消费 1 元累计 1 积分' },
  { name: '积分兑换', detail: '100 积分抵 10 元消费金' },
  { name: '会员折扣', detail: '白银及以上等级享 95 折' },
  { name: '积分效期', detail: '自获取日起 12 个月内有效' }
]
let cardFlipSwapTimer = null
let cardFlipDoneTimer = null

function flipCard() {
  if (isCardAnimating.value) return
  isCardAnimating.value = true
  isCardFlipped.value = !isCardFlipped.value

  // 旋转至 90°（卡片侧立、画面最薄）的瞬间切换正反面内容，
  // 让“换面”发生在视觉盲区，翻转观感连续无跳变
  cardFlipSwapTimer = setTimeout(() => {
    showCardBack.value = isCardFlipped.value
  }, CARD_FLIP_DURATION / 2)

  cardFlipDoneTimer = setTimeout(() => {
    isCardAnimating.value = false
  }, CARD_FLIP_DURATION + 20)
}

onUnmounted(() => {
  clearTimeout(cardFlipSwapTimer)
  clearTimeout(cardFlipDoneTimer)
})

onShow(async () => {
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
})

function showFeature(title) {
  uni.showModal({
    title,
    content: '该会员服务当前仅展示权益说明，积分兑换与优惠券核销尚未开放。',
    showCancel: false,
    confirmText: '知道了'
  })
}

function handleTabSelect(tab) {
  if (tab === 'member') return

  if (tab === 'home') {
    resetToPage('index')
    return
  }

  if (tab === 'orders') {
    if (shop.order || shop.orderHistory.length) {
      resetToPage('order')
    } else {
      uni.showToast({ title: '暂无订单，先选一杯咖啡吧', icon: 'none' })
    }
    return
  }

  if (tab === 'profile') {
    resetToPage('profile')
    return
  }

  uni.showToast({ title: '个人中心即将开放', icon: 'none' })
}
</script>

<style scoped>
.member-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  background: #f7f6f2;
}

/* 顶栏不参与伸缩（原 .member-screen > view:first-child，小程序端首个节点是 phone-header） */
.screen-header {
  flex: 0 0 auto;
}

.member-content {
  width: 100%;
  flex: 1 1 auto;
  height: 0;
  min-height: 0;
  padding: 24rpx 36rpx calc(230rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* ===== 3D 翻转会员卡 ===== */
/* 外层只负责视距与投影：perspective 提供纵深，阴影不随卡片旋转，翻转时更稳定 */
.member-card {
  perspective: 1800rpx;
  border-radius: 22rpx;
  box-shadow: 0 14rpx 30rpx rgba(25, 21, 11, 0.16);
  transition: transform 160ms ease-out, box-shadow 160ms ease-out;
}

/* 按压反馈（hover-class） */
.member-card--pressed {
  transform: scale(0.985);
  box-shadow: 0 8rpx 18rpx rgba(25, 21, 11, 0.2);
}

/* 旋转层：所有 3D 变换都发生在这一层 */
.card-rotor {
  position: relative;
  height: 360rpx;
  transform-style: preserve-3d;
  /* 对称缓动保证走到一半（350ms）时恰好侧立 90°，换面正好落在视觉盲区 */
  transition: transform 700ms cubic-bezier(0.65, 0, 0.35, 1);
  will-change: transform;
}

.card-rotor.is-flipped {
  transform: rotateY(180deg);
}

/* 正反两面绝对定位叠放；背面预旋转 180°，翻到背面时恰好正向朝人 */
.card-face {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 30rpx 36rpx;
  box-sizing: border-box;
  border: 2rpx solid #b99737;
  border-radius: 22rpx;
  color: #e8d69b;
  background: radial-gradient(circle at 90% 10%, #3c3421 0, transparent 38%),
    linear-gradient(135deg, #10100f 0%, #24221b 58%, #0c0c0b 100%);
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
}

/* 两面共享的装饰圆环 */
.card-face::after {
  position: absolute;
  right: -82rpx;
  bottom: -160rpx;
  width: 350rpx;
  height: 350rpx;
  border: 1rpx solid rgba(212, 173, 47, 0.25);
  border-radius: 50%;
  content: '';
}

/* 背面：渐变方向镜像 + 右上角星芒水印，与正面区分 */
.card-face--back {
  transform: rotateY(180deg);
  background: radial-gradient(circle at 10% 90%, #3c3421 0, transparent 38%),
    linear-gradient(225deg, #10100f 0%, #24221b 58%, #0c0c0b 100%);
}

.card-face--back::before {
  position: absolute;
  top: -44rpx;
  right: 6rpx;
  color: rgba(212, 173, 47, 0.08);
  font-size: 150rpx;
  content: '✦';
}

.card-top,
.card-middle,
.card-bottom {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.card-middle {
  align-items: center;
}

/* 金色芯片：会员卡的实体质感记忆点 */
.card-chip {
  position: relative;
  width: 62rpx;
  height: 46rpx;
  overflow: hidden;
  border-radius: 8rpx;
  background: linear-gradient(135deg, #e9d089 0%, #b99737 55%, #8a6d1f 100%);
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, 0.35);
}

.card-chip::before {
  position: absolute;
  top: 50%;
  right: 8rpx;
  left: 8rpx;
  height: 14rpx;
  border-top: 2rpx solid rgba(90, 68, 12, 0.55);
  border-bottom: 2rpx solid rgba(90, 68, 12, 0.55);
  transform: translateY(-50%);
  content: '';
}

.flip-hint {
  color: rgba(228, 200, 107, 0.55);
  font-size: 18rpx;
  letter-spacing: 2rpx;
}

.card-block,
.points-block {
  min-width: 0;
}

.brand {
  display: block;
  color: #e4c86b;
  font-family: 'Songti SC', SimSun, serif;
  font-size: 44rpx;
  letter-spacing: 5rpx;
}

.brand-caption,
.card-label {
  display: block;
  color: #aaa38e;
  font-size: 17rpx;
  letter-spacing: 2rpx;
}

.brand-caption {
  margin-top: 8rpx;
}

.level-badge {
  flex-shrink: 0;
  max-width: 48%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 10rpx 18rpx;
  border: 1rpx solid #b99737;
  border-radius: 24rpx;
  color: #e4c86b;
  font-size: 21rpx;
}

.card-bottom {
  align-items: flex-end;
}

.card-number {
  display: block;
  margin-top: 12rpx;
  color: #e2cf94;
  font-size: 27rpx;
  letter-spacing: 3rpx;
  white-space: nowrap;
}

.points-block {
  text-align: right;
}

.points {
  display: block;
  margin-top: 5rpx;
  color: #e4c86b;
  font-size: 44rpx;
  font-weight: 600;
}

/* 背面：规则说明版式 */
.rules-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.rules-title {
  color: #e4c86b;
  font-family: 'Songti SC', SimSun, serif;
  font-size: 30rpx;
  letter-spacing: 4rpx;
}

.rules-caption {
  color: #aaa38e;
  font-size: 16rpx;
  letter-spacing: 2rpx;
}

.rules-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-evenly;
  min-height: 0;
  margin: 4rpx 0;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-width: 0;
}

.rule-dot {
  flex-shrink: 0;
  color: #d4ad2f;
  font-size: 14rpx;
}

.rule-name {
  flex-shrink: 0;
  color: #e8d69b;
  font-size: 21rpx;
  font-weight: 600;
}

.rule-detail {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #aaa38e;
  font-size: 19rpx;
}

.flip-back-hint {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: rgba(228, 200, 107, 0.5);
  font-size: 17rpx;
  letter-spacing: 2rpx;
}

.summary-card {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
  padding: 27rpx 8rpx;
  border: 1rpx solid #eee9dc;
  border-radius: 18rpx;
  background: #fff;
}

.summary-item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  min-width: 0;
}

.summary-value {
  color: #28251d;
  font-size: 31rpx;
  font-weight: 600;
}

.summary-label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #898989;
  font-size: 20rpx;
}

.summary-divider {
  width: 1rpx;
  height: 58rpx;
  background: #eee;
}

.growth-track {
  width: 104rpx;
  max-width: 100%;
  height: 7rpx;
  margin-top: 2rpx;
  overflow: hidden;
  border-radius: 8rpx;
  background: #eeeae0;
}

.growth-progress {
  width: 64%;
  height: 100%;
  border-radius: inherit;
  background: #d4ad2f;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 38rpx 2rpx 20rpx;
  color: #24221d;
  font-size: 30rpx;
  font-weight: 600;
}

.section-note {
  color: #999;
  font-size: 19rpx;
  font-weight: 400;
}

.benefits-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.benefit-card {
  display: flex;
  width: calc(50% - 8rpx);
  min-width: 0;
  min-height: 160rpx;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 20rpx 24rpx;
  border: 1rpx solid #eee9dc;
  border-radius: 16rpx;
  background: #fff;
  box-sizing: border-box;
  margin-bottom: 16rpx;
}

.benefit-icon {
  color: #c5a13b;
  font-size: 34rpx;
  line-height: 1.1;
}

.benefit-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 10rpx;
  color: #292720;
  font-size: 24rpx;
  font-weight: 600;
}

.benefit-description {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 5rpx;
  color: #92908a;
  font-size: 19rpx;
}

.activity-card {
  position: relative;
  display: flex;
  min-height: 190rpx;
  align-items: center;
  justify-content: space-between;
  margin-top: 26rpx;
  overflow: hidden;
  padding: 26rpx 30rpx;
  border: 1rpx solid #b99737;
  border-radius: 18rpx;
  color: #eee3c1;
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
  color: #c7a846;
  font-size: 16rpx;
  letter-spacing: 2rpx;
}

.activity-title {
  display: block;
  margin-top: 10rpx;
  font-family: 'Songti SC', SimSun, serif;
  font-size: 26rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-description {
  display: block;
  margin-top: 8rpx;
  color: #aaa69b;
  font-size: 18rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-link {
  display: block;
  margin-top: 13rpx;
  color: #d4ad2f;
  font-size: 19rpx;
}

.activity-mark {
  position: absolute;
  right: 27rpx;
  color: rgba(212, 173, 47, 0.22);
  font-size: 130rpx;
}

.member-footnote {
  padding: 22rpx 0 8rpx;
  color: #aaa;
  font-size: 18rpx;
  text-align: center;
}

.member-footer {
  right: 0;
  bottom: 0;
  left: 0;
  padding-bottom: 0;
  box-sizing: border-box;
}

@media (max-width: 340px) {
  .member-content {
    padding-left: 24rpx;
    padding-right: 24rpx;
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
    gap: 8rpx;
  }
}

/* 减弱动效偏好：关闭翻转动画（点击后内容仍会切换） */
@media (prefers-reduced-motion: reduce) {
  .card-rotor {
    transition-duration: 1ms;
  }
}
</style>
