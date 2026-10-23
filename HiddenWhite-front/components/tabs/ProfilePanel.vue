<template>
  <!-- 我的面板：账号信息、订单概览与常用服务 -->
  <scroll-view class="panel-scroll profile-panel" scroll-y>
    <view class="profile-hero reveal">
      <view class="avatar"><text class="avatar-text">白</text></view>
      <view class="profile-identity">
        <text class="profile-name">{{ isLoggedIn ? currentUser.displayName : '隐白客人' }}</text>
        <text class="profile-caption">{{ isLoggedIn ? currentUser.phone : '登录后享受更多会员礼遇' }}</text>
      </view>
      <text
        class="guest-badge"
        hover-class="is-press"
        :hover-stay-time="60"
        @tap="handleAuth"
      >{{ isLoggedIn ? '退出登录' : '登录 / 注册' }}</text>
    </view>

    <view
      class="member-card reveal reveal-1"
      hover-class="member-card--press"
      :hover-stay-time="60"
      @tap="$emit('switch-tab', 'member')"
    >
      <view class="member-card-copy">
        <text class="member-kicker">HIDDEN WHITE · MEMBER</text>
        <text class="member-title">让每一杯都有礼遇</text>
        <view class="member-link">
          <text>查看会员权益</text>
          <Icon name="chevron-right" tone="gold-on-dark" :size="22" />
        </view>
      </view>
      <view class="member-mark">
        <Icon name="sparkle" tone="decor" :size="110" />
      </view>
    </view>

    <view class="orders-card reveal reveal-2">
      <view class="card-head">
        <text class="card-title">我的订单</text>
        <view class="all-orders" hover-class="all-orders--press" :hover-stay-time="60" @tap="openOrders">
          <text>全部订单</text>
          <Icon name="chevron-right" tone="faint" :size="22" />
        </view>
      </view>

      <view class="stat-grid">
        <view class="stat-cell" hover-class="stat-cell--press" :hover-stay-time="60" @tap="openOrders">
          <text class="stat-value">{{ activeOrderCount }}</text>
          <text class="stat-label">进行中</text>
        </view>
        <view class="stat-sep" />
        <view class="stat-cell" hover-class="stat-cell--press" :hover-stay-time="60" @tap="openOrders">
          <text class="stat-value">{{ shop.orderHistory.length }}</text>
          <text class="stat-label">历史订单</text>
        </view>
        <view class="stat-sep" />
        <view class="stat-cell" hover-class="stat-cell--press" :hover-stay-time="60" @tap="openOrders">
          <Icon name="cup" tone="gold" :size="32" />
          <text class="stat-label">到店自提</text>
        </view>
      </view>

      <view v-if="shop.order" class="latest-order" hover-class="latest-order--press" :hover-stay-time="60" @tap="openOrders">
        <view>
          <text class="latest-order-label">最新取餐号</text>
          <text class="latest-order-number gold">{{ shop.order.pickup }}</text>
        </view>
        <view class="latest-order-status">
          <text>制作中</text>
          <Icon name="chevron-right" tone="faint" :size="22" />
        </view>
      </view>
      <view v-else class="latest-order" hover-class="latest-order--press" :hover-stay-time="60" @tap="$emit('switch-tab', 'home')">
        <text>还没有进行中的订单</text>
        <view class="latest-order-status">
          <text class="gold">去点一杯</text>
          <Icon name="chevron-right" tone="gold" :size="22" />
        </view>
      </view>
    </view>

    <view class="service-card reveal reveal-3">
      <text class="card-title service-heading">常用服务</text>
      <view class="row-list">
        <view class="row-item" hover-class="row-item--press" :hover-stay-time="60" @tap="$emit('switch-tab', 'member')">
          <view class="row-icon"><Icon name="sparkle" tone="gold" :size="26" /></view>
          <text class="row-title">会员中心</text>
          <text class="row-desc">积分与专属礼遇</text>
          <Icon name="chevron-right" tone="faint" :size="24" />
        </view>
        <view class="row-item" hover-class="row-item--press" :hover-stay-time="60" @tap="showStoreInfo">
          <view class="row-icon"><Icon name="store" tone="gold" :size="26" /></view>
          <text class="row-title">门店信息</text>
          <text class="row-desc">地址与营业时间</text>
          <Icon name="chevron-right" tone="faint" :size="24" />
        </view>
        <view class="row-item" hover-class="row-item--press" :hover-stay-time="60" @tap="showFeature('帮助与反馈')">
          <view class="row-icon"><Icon name="help" tone="gold" :size="26" /></view>
          <text class="row-title">帮助与反馈</text>
          <text class="row-desc">我们随时为你提供帮助</text>
          <Icon name="chevron-right" tone="faint" :size="24" />
        </view>
      </view>
    </view>

    <view class="profile-note">
      {{ isLoggedIn ? '订单与会员信息已绑定当前账号' : '登录后即可点单，订单信息会安全保存' }}
    </view>
    <view class="dev-tools" hover-class="dev-tools--press" :hover-stay-time="60" @tap="openBackendSetting" @longpress="onBackendLongPress">
      <view class="dev-row">
        <text class="dev-label">后端服务</text>
        <text class="dev-value" :class="backendOk ? 'ok' : 'bad'">
          {{ backendOk ? '已连接' : '未连接' }}
        </text>
      </view>
      <text class="dev-address">{{ backendAddress || '正在检测…' }} · 点击修改，长按自动探测</text>
    </view>
  </scroll-view>
</template>

<script setup>
import { computed, ref } from 'vue'
import Icon from '../ui/Icon.vue'
import { shop, refreshOrders, invalidateCatalog, clearOrderState } from '../../store/index'
import { currentUser, isLoggedIn, logout } from '../../store/auth'
import { openPage, showStoreInfo } from '../../utils/navigation'
import { showNotice } from '../../utils/toast'
import { checkApiHealth, resetApiBaseUrl, setApiBaseUrl } from '../../config/backend'

const emit = defineEmits(['switch-tab'])

const activeOrderCount = computed(() => shop.order ? 1 : 0)

// 后端连接状态：换网络后能直接看出「连不上」并当场改地址，不用再去翻代码
const backendOk = ref(false)
const backendAddress = ref('')

async function checkBackend() {
  const health = await checkApiHealth()
  backendOk.value = health.ok
  backendAddress.value = health.baseUrl
}

// 壳页面在切到本面板 / 页面 onShow 时调用
async function activate() {
  checkBackend()

  try {
    await refreshOrders()
  } catch (error) {
    uni.showToast({ title: error.message || '订单信息加载失败', icon: 'none' })
  }
}

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
  showNotice(title, '当前为体验版本，该服务暂未开放。你仍可浏览菜单、创建演示订单并查看取餐记录。')
}

function openOrders() {
  emit('switch-tab', 'orders')
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
      // 只清内存不够：上一账号的订单还留在 storage 里，
      // 下次冷启动会被读回来，连带把新会员的首单减免判掉
      clearOrderState()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
/* 与其余三个面板统一横向留白与底部预留 */
.profile-panel {
  padding: $pad-panel-top $gutter $pad-panel-plain;
  box-sizing: border-box;
}

/* 小程序端：与首页一致，整体下移避让右上角胶囊按钮 */
/* #ifdef MP-WEIXIN */
.profile-panel {
  padding-top: $pad-capsule;
}
/* #endif */

/* ---- 账号行 ---- */
.profile-hero {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: $sp-4;
  padding: 0 0 $sp-4;
}

.avatar {
  display: flex;
  width: 100rpx;
  height: 100rpx;
  flex: 0 0 100rpx;
  align-items: center;
  justify-content: center;
  border: 2rpx solid $c-gold-line;
  border-radius: 50%;
  background: linear-gradient(145deg, $c-dark-soft, $c-dark);
}

.avatar-text {
  color: $c-gold-on-dark;
  font-family: $font-display;
  font-size: $fs-h2;
}

.profile-identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: $sp-0;
}

.profile-name {
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.profile-caption {
  color: $c-ink-muted;
  font-size: $fs-micro;
  @include ellipsis;
}

/* 登录/退出入口：触控高度 88rpx（44px） */
.guest-badge {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  min-height: $tap-min;
  padding: 0 $sp-4;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  background: $c-gold-soft;
  color: $c-gold-text;
  font-size: $fs-micro;
  transition: transform $dur-instant $ease-out, box-shadow $dur-fast ease;
}

/* ---- 会员入口卡 ---- */
.member-card {
  position: relative;
  display: flex;
  min-height: 190rpx;
  align-items: center;
  overflow: hidden;
  padding: $sp-5 $sp-6;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-lg;
  background: radial-gradient(circle at 90% 10%, $c-dark-glint 0, transparent 38%),
    linear-gradient(120deg, $c-dark, $c-dark-2);
  transition: transform $dur-instant $ease-out, border-color $dur-fast ease;
}

.member-card--press {
  transform: scale(0.985);
}

.member-card-copy {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: $sp-2;
}

.member-kicker {
  @include caps;
  color: $c-gold-on-dark;
}

.member-title {
  font-family: $font-display;
  font-size: $fs-title-sm;
  color: $c-on-dark;
}

.member-link {
  display: flex;
  align-items: center;
  gap: $sp-0;
  color: $c-gold-on-dark;
  font-size: $fs-micro;
}

/* 底纹星标：极低不透明度，纯氛围 */
.member-mark {
  position: absolute;
  right: 36rpx;
  opacity: 0.22;
  pointer-events: none;
}

/* ---- 订单概览 ---- */
.orders-card,
.service-card {
  margin-top: $sp-4;
  padding: $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
}

.all-orders {
  display: flex;
  align-items: center;
  gap: $sp-0;
  min-height: $tap-min;
  padding-left: $sp-3;
  color: $c-ink-muted;
  font-size: $fs-micro;
  font-weight: 400;
}

/* 卡片内的统计格去掉自身的描边与阴影：外层卡已经有了一层，叠起来是双线 */
.orders-card .stat-grid {
  padding: 0;
  margin-top: $sp-1;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.all-orders--press {
  opacity: 0.6;
}

.latest-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  min-height: $tap-min;
  margin-top: $sp-4;
  padding: $sp-2 $sp-4;
  border-radius: $r-md;
  background: $c-surface-2;
  color: $c-ink-muted;
  font-size: $fs-micro;
  transition: background-color $dur-fast ease;
}

.latest-order--press {
  background: $c-surface-3;
}

.latest-order-label {
  display: block;
  color: $c-ink-subtle;
  font-size: $fs-micro;
}

.latest-order-number {
  display: block;
  margin-top: 2rpx;
  font-family: $font-display;
  font-size: $fs-title;
  font-weight: 600;
  @include numeric;
}

.latest-order-status {
  display: flex;
  align-items: center;
  gap: $sp-0;
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---- 常用服务 ---- */
.service-heading {
  margin-bottom: $sp-1;
}

.row-item--press {
  background: $c-surface-2;
}

/* ---- 脚注与调试入口 ---- */
.profile-note {
  padding: $sp-5 0 $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
}

.dev-tools {
  display: flex;
  min-height: $tap-min;
  flex-direction: column;
  justify-content: center;
  margin-top: $sp-2;
  padding: $sp-4 $sp-5;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-md;
  background: $c-surface-2;
  transition: background-color $dur-fast ease;
}

.dev-tools--press {
  background: $c-surface-3;
}

.dev-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.dev-label {
  color: $c-ink-muted;
  font-size: $fs-caption;
}

.dev-value {
  font-size: $fs-micro;
}

.dev-value.ok {
  color: $c-success;
}

.dev-value.bad {
  color: $c-danger;
}

/* 原先 #a8a49a 对浅底仅约 2.6:1，几乎看不清 */
.dev-address {
  display: block;
  margin-top: $sp-1;
  color: $c-ink-subtle;
  font-size: $fs-micro;
  word-break: break-all;
}

/* ---- 交互态 ---- */
/* #ifdef H5 */
.stat-cell:hover,
.row-item:hover,
.all-orders:hover,
.latest-order:hover {
  background: $c-surface-2;
}

.member-card:hover {
  border-color: $c-gold;
}
/* #endif */

@media (max-width: 340px) {
  .profile-panel {
    padding-right: $sp-4;
    padding-left: $sp-4;
  }

  .profile-hero {
    gap: $sp-3;
  }

  .avatar {
    width: 84rpx;
    height: 84rpx;
    flex-basis: 84rpx;
  }

  .guest-badge {
    padding-right: $sp-3;
    padding-left: $sp-3;
  }

  .row-item {
    gap: $sp-2;
  }
}
</style>
