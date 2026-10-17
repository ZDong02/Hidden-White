<template>
  <view class="screen profile-screen">
    <PhoneHeader title="我的" class="screen-header" />

    <scroll-view class="profile-content" scroll-y>
      <view class="profile-hero">
        <view class="avatar"><text>白</text></view>
        <view class="profile-identity">
        <text class="profile-name">{{ isLoggedIn ? currentUser.displayName : '隐白客人' }}</text>
        <text class="profile-caption">{{ isLoggedIn ? currentUser.phone : '登录后享受更多会员礼遇' }}</text>
        </view>
        <text class="guest-badge" @tap="handleAuth">{{ isLoggedIn ? '退出登录' : '登录 / 注册' }}</text>
      </view>

      <view class="member-card" @tap="openMember">
        <view class="member-card-copy">
          <text class="member-kicker">HIDDEN WHITE · MEMBER</text>
          <text class="member-title">让每一杯都有礼遇</text>
          <text class="member-link">查看会员权益 ›</text>
        </view>
        <text class="member-mark">✦</text>
      </view>

      <view class="orders-card">
        <view class="section-heading">
          <text>我的订单</text>
          <view class="all-orders" @tap="openOrders">全部订单 <text class="all-orders-arrow">›</text></view>
        </view>
        <view class="order-summary">
          <view class="order-stat" @tap="openOrders">
            <text class="order-stat-value">{{ activeOrderCount }}</text>
            <text class="order-stat-label">进行中</text>
          </view>
          <view class="order-stat-divider" />
          <view class="order-stat" @tap="openOrders">
            <text class="order-stat-value">{{ shop.orderHistory.length }}</text>
            <text class="order-stat-label">历史订单</text>
          </view>
          <view class="order-stat-divider" />
          <view class="order-stat" @tap="openOrders">
            <text class="order-stat-icon">☕</text>
            <text class="order-stat-label">到店自提</text>
          </view>
        </view>
        <view v-if="shop.order" class="latest-order" @tap="openOrders">
          <view>
            <text class="latest-order-label">最新取餐号</text>
            <text class="latest-order-number gold">{{ shop.order.pickup }}</text>
          </view>
          <text class="latest-order-status">制作中 ›</text>
        </view>
        <view v-else class="order-empty" @tap="openMenu">
          <text>还没有进行中的订单</text>
          <text class="gold">去点一杯 ›</text>
        </view>
      </view>

      <view class="service-card">
        <text class="section-heading service-heading">常用服务</text>
        <view class="service-row" @tap="openMember">
          <view class="service-icon gold">✦</view>
          <text class="service-title">会员中心</text>
          <text class="service-description">积分与专属礼遇</text>
          <text class="service-arrow">›</text>
        </view>
        <view class="service-row" @tap="showStore">
          <view class="service-icon">⌖</view>
          <text class="service-title">门店信息</text>
          <text class="service-description">地址与营业时间</text>
          <text class="service-arrow">›</text>
        </view>
        <view class="service-row" @tap="showFeature('帮助与反馈')">
          <view class="service-icon">?</view>
          <text class="service-title">帮助与反馈</text>
          <text class="service-description">我们随时为你提供帮助</text>
          <text class="service-arrow">›</text>
        </view>
      </view>

      <view class="profile-note">{{ isLoggedIn ? '订单与会员信息已绑定当前账号' : '登录后即可点单，订单信息会安全保存' }}</view>
      <view class="dev-tools" @tap="openBackendSetting" @longpress="onBackendLongPress">
        <view class="dev-row">
          <text class="dev-label">后端服务</text>
          <text class="dev-value" :class="backendOk ? 'ok' : 'bad'">
            {{ backendOk ? '已连接' : '未连接' }}
          </text>
        </view>
        <text class="dev-address">{{ backendAddress || '正在检测…' }} · 点击修改，长按自动探测</text>
      </view>
    </scroll-view>

    <view class="bottom-fixed profile-footer">
      <MainTabBar active="profile" @select="handleTabSelect" />
      <HomeIndicator />
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import MainTabBar from '../../components/MainTabBar.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import { shop, refreshOrders, invalidateCatalog } from '../../store/index'
import { currentUser, isLoggedIn, logout } from '../../store/auth'
import { openPage, resetToPage, showStoreInfo } from '../../utils/navigation'
import { checkApiHealth, resetApiBaseUrl, setApiBaseUrl } from '../../config/backend'

const activeOrderCount = computed(() => shop.order ? 1 : 0)

// 后端连接状态：换网络后能直接看出「连不上」并当场改地址，不用再去翻代码
const backendOk = ref(false)
const backendAddress = ref('')

async function checkBackend() {
  const health = await checkApiHealth()
  backendOk.value = health.ok
  backendAddress.value = health.baseUrl
}

onShow(async () => {
  checkBackend()

  try {
    await refreshOrders()
  } catch (error) {
    uni.showToast({ title: error.message || '订单信息加载失败', icon: 'none' })
  }
})

// 打开后端地址设置：可手动填写、自动探测或恢复默认
function openBackendSetting() {
  uni.showModal({
    title: '后端服务地址',
    content: `当前：${backendAddress.value || '未检测'}\n状态：${backendOk.value ? '已连接' : '未连接'}\n\n可直接填写新地址，如 http://10.30.120.150:8080`,
    editable: true,
    placeholderText: 'http://IP:8080',
    confirmText: '保存',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) return
      const value = (res.content || '').trim()
      if (value) {
        setApiBaseUrl(value)
        // 地址变了，商品与门店缓存需失效，否则会继续展示旧数据
        invalidateCatalog()
      }
      const health = await checkApiHealth()
      backendOk.value = health.ok
      backendAddress.value = health.baseUrl
      if (value && !health.ok) {
        uni.showModal({
          title: '仍然无法连接',
          content: `地址已保存为 ${health.baseUrl}，但连接失败。\n\n请确认：\n1. Spring Boot 已启动\n2. 手机与电脑在同一局域网\n3. 电脑防火墙已放行 8080 端口\n\n点「重置」可恢复自动探测。`,
          confirmText: '知道了'
        })
        return
      }
      if (health.ok) {
        invalidateCatalog()
        uni.showToast({ title: '连接成功', icon: 'success' })
      }
    }
  })
}

// 长按可恢复自动探测（清除手动配置）
function onBackendLongPress() {
  resetApiBaseUrl()
  invalidateCatalog()
  checkBackend()
  uni.showToast({ title: '已恢复自动探测', icon: 'none' })
}

function showFeature(title) {
  uni.showModal({
    title,
    content: '当前为体验版本，该服务暂未开放。你仍可浏览菜单、创建演示订单并查看取餐记录。',
    showCancel: false,
    confirmText: '知道了'
  })
}

function showStore() {
  showStoreInfo()
}

function openMenu() {
  resetToPage('index')
}

function openMember() {
  resetToPage('member')
}

function openOrders() {
  resetToPage('order')
}

function handleAuth() {
  if (!isLoggedIn.value) {
    openPage('auth')
    return
  }

  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新登录才能下单。',
    success: async (res) => {
      if (!res.confirm) return
      await logout()
      shop.order = null
      shop.orderHistory = []
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

function handleTabSelect(tab) {
  if (tab === 'home') {
    resetToPage('index')
  } else if (tab === 'orders') {
    openOrders()
  } else if (tab === 'member') {
    openMember()
  }
}
</script>

<style scoped>
.profile-screen {
  display: flex;
  height: 100vh;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #f7f6f2;
}

/* 顶栏不参与伸缩（原 .profile-screen > view:first-child，小程序端首个节点是 phone-header） */
.screen-header {
  flex: 0 0 auto;
}

.profile-content {
  width: 100%;
  height: 0;
  min-height: 0;
  flex: 1 1 auto;
  box-sizing: border-box;
  padding: 22rpx 36rpx calc(230rpx + env(safe-area-inset-bottom));
}

.profile-hero {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 20rpx;
  padding: 12rpx 0 25rpx;
}

.avatar {
  display: flex;
  width: 100rpx;
  height: 100rpx;
  flex: 0 0 100rpx;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #d3b65d;
  border-radius: 50%;
  color: #e4cd82;
  background: linear-gradient(145deg, #29261d, #11110f);
  font-family: 'Songti SC', SimSun, serif;
  font-size: 46rpx;
}

.profile-identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 9rpx;
}

.profile-name {
  color: #24221d;
  font-size: 31rpx;
  font-weight: 600;
}

.profile-caption {
  overflow: hidden;
  color: #92908a;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guest-badge {
  flex: 0 0 auto;
  padding: 13rpx 17rpx;
  border: 1rpx solid #c8a841;
  border-radius: 28rpx;
  color: #8b6c12;
  background: #fffdf7;
  font-size: 19rpx;
  line-height: 1.3;
}

.member-card {
  position: relative;
  display: flex;
  min-height: 190rpx;
  align-items: center;
  overflow: hidden;
  padding: 27rpx 30rpx;
  border: 1rpx solid #b99737;
  border-radius: 19rpx;
  color: #eee3c1;
  background: radial-gradient(circle at 90% 10%, #3c3421 0, transparent 38%),
    linear-gradient(120deg, #11110f, #252218);
}

.member-card-copy {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 11rpx;
}

.member-kicker {
  color: #c7a846;
  font-size: 16rpx;
  letter-spacing: 2rpx;
}

.member-title {
  font-family: 'Songti SC', SimSun, serif;
  font-size: 29rpx;
}

.member-link {
  color: #d4ad2f;
  font-size: 20rpx;
}

.member-mark {
  position: absolute;
  right: 25rpx;
  z-index: 1;
  color: rgba(212, 173, 47, 0.2);
  font-size: 125rpx;
}

.orders-card,
.service-card {
  margin-top: 22rpx;
  padding: 25rpx 26rpx;
  border: 1rpx solid #eee9dc;
  border-radius: 19rpx;
  background: #fff;
  box-shadow: 0 8rpx 20rpx rgba(25, 21, 11, 0.035);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #24221d;
  font-size: 27rpx;
  font-weight: 600;
}

.all-orders {
  color: #999;
  font-size: 20rpx;
  font-weight: 400;
}

/* 箭头「›」字号比文字大一号（原 .all-orders text） */
.all-orders-arrow {
  margin-left: 7rpx;
  font-size: 32rpx;
}

.order-summary {
  display: flex;
  align-items: center;
  margin-top: 26rpx;
}

.order-stat {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.order-stat-value {
  color: #28251d;
  font-size: 31rpx;
  font-weight: 600;
}

.order-stat-icon {
  height: 45rpx;
  color: #c5a13b;
  font-size: 34rpx;
  line-height: 45rpx;
}

.order-stat-label {
  color: #898989;
  font-size: 19rpx;
}

.order-stat-divider {
  width: 1rpx;
  height: 55rpx;
  background: #eee;
}

.latest-order,
.order-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 23rpx;
  padding: 17rpx 18rpx;
  border-radius: 13rpx;
  background: #f8f6ef;
}

.latest-order-label {
  display: block;
  color: #898989;
  font-size: 18rpx;
}

.latest-order-number {
  display: block;
  margin-top: 4rpx;
  font-size: 34rpx;
  font-weight: 600;
}

.latest-order-status,
.order-empty {
  color: #777;
  font-size: 19rpx;
}

.order-empty {
  color: #898989;
}

.service-heading {
  margin-bottom: 10rpx;
}

.service-row {
  display: flex;
  min-height: 88rpx;
  align-items: center;
  gap: 14rpx;
  border-top: 1rpx solid #f1f0ec;
}

.service-icon {
  display: flex;
  width: 42rpx;
  height: 42rpx;
  flex: 0 0 42rpx;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  color: #645f50;
  background: #f6f4ed;
  font-size: 28rpx;
}

.service-title {
  flex: 0 0 auto;
  color: #34322c;
  font-size: 22rpx;
}

.service-description {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: #999;
  font-size: 18rpx;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-arrow {
  flex: 0 0 auto;
  color: #aaa;
  font-size: 32rpx;
}

.profile-note {
  padding: 24rpx 0 8rpx;
  color: #aaa;
  font-size: 18rpx;
  text-align: center;
}

.dev-tools {
  margin-top: 14rpx;
  padding: 22rpx 26rpx;
  border: 1px dashed #e0dbcc;
  border-radius: 16rpx;
  background: #fbfaf6;
}

.dev-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dev-label {
  color: #6b675c;
  font-size: 22rpx;
}

.dev-value {
  font-size: 21rpx;
}

.dev-value.ok {
  color: #4a7c2f;
}

.dev-value.bad {
  color: #c0392b;
}

.dev-address {
  display: block;
  margin-top: 10rpx;
  color: #a8a49a;
  font-size: 18rpx;
  word-break: break-all;
}

.profile-footer {
  right: 0;
  bottom: 0;
  left: 0;
  padding-bottom: 0;
  box-sizing: border-box;
}

@media (max-width: 340px) {
  .profile-content {
    padding-right: 24rpx;
    padding-left: 24rpx;
  }

  .profile-hero {
    gap: 13rpx;
  }

  .avatar {
    width: 84rpx;
    height: 84rpx;
    flex-basis: 84rpx;
  }

  .guest-badge {
    padding-right: 12rpx;
    padding-left: 12rpx;
    font-size: 18rpx;
  }

  .service-row {
    gap: 9rpx;
  }
}
</style>
