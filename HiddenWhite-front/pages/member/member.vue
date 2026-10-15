<template>
  <view class="screen member-screen">
    <PhoneHeader title="会员中心" />

    <scroll-view class="member-content" scroll-y>
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

    <view class="bottom-fixed member-footer">
      <MainTabBar active="member" @select="handleTabSelect" />
      <HomeIndicator />
    </view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
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

.member-screen > view:first-child {
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

.member-card {
  position: relative;
  min-height: 330rpx;
  overflow: hidden;
  padding: 36rpx;
  border: 2rpx solid #b99737;
  border-radius: 22rpx;
  color: #e8d69b;
  background: radial-gradient(circle at 90% 10%, #3c3421 0, transparent 38%),
    linear-gradient(135deg, #10100f 0%, #24221b 58%, #0c0c0b 100%);
  box-shadow: 0 14rpx 30rpx rgba(25, 21, 11, 0.16);
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
  margin-top: 70rpx;
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
</style>
