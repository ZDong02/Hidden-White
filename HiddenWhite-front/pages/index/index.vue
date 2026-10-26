<template>
  <!-- 壳页面：四个 Tab 面板通过横向 swiper 切换，底部固定分段控件与内容区同步移动 -->
  <view class="screen shell-screen">
    <PhoneHeader class="screen-header" />

    <swiper
      class="tab-swiper"
      :current="activeIndex"
      :duration="300"
      easing-function="easeOutCubic"
      @change="onSwiperChange"
    >
      <swiper-item>
        <MenuPanel ref="menuPanel" @switch-tab="switchTo" />
      </swiper-item>
      <swiper-item>
        <OrdersPanel ref="ordersPanel" @show-voucher="voucherOpen = true" />
      </swiper-item>
      <swiper-item>
        <MemberPanel ref="memberPanel" />
      </swiper-item>
      <swiper-item>
        <ProfilePanel ref="profilePanel" @switch-tab="switchTo" />
      </swiper-item>
    </swiper>

    <!-- 底部固定区：随 Tab 变化的业务条 + 分段控件 + Home Indicator -->
    <view class="bottom-fixed shell-footer">
      <view v-if="activeTab === 'home'" class="cart-bar">
        <view class="cart-copy" @tap="bagOpen = true">
          <text class="cart-caption muted">
            已选 <text :key="count" class="cart-count">{{ count }}</text> 杯 · 门店自提
          </text>
          <text class="cart-total gold">{{ subtotal }}</text>
        </view>
        <UiButton
          variant="primary"
          :disabled="!count"
          @tap="goToCheckout"
          style="width: 336rpx; flex-shrink: 0;"
        >
          去结算
        </UiButton>
      </view>

      <template v-else-if="activeTab === 'orders' && order">
        <text class="muted order-hint">到店后向咖啡师出示取餐号即可</text>
        <!-- 滑动确认胶囊：替代原「确认已取餐 / 查看取餐凭证」双按钮 -->
        <view class="order-footer-actions">
          <SlideConfirm ref="slideConfirm" @confirm="completeCurrentOrder" />
        </view>
        <!--
          取消订单：仅未支付时展示。
          已支付订单不给这个选项——钱已到账，取消须走退款流程，前端直接放行会造成资损。
        -->
        <view v-if="order.status !== 'in-progress'" class="order-cancel-row">
          <text class="order-cancel-link" hover-class="order-cancel-link--press" :hover-stay-time="60" @tap="confirmCancelOrder">取消订单</text>
        </view>
      </template>

      <MainTabBar :active="activeTab" @select="switchTo" />
      <HomeIndicator />
    </view>

    <!-- 购物袋弹窗 -->
    <Modal v-model:visible="bagOpen" title="购物袋" :mask-closable="true">
      <view
        v-for="(item, index) in shop.cart"
        :key="`${item.id}-${item.size}-${item.temperature}-${item.milk}`"
        class="bag-item"
      >
        <view class="bag-copy">
          <text class="product-name">{{ item.name }}</text>
          <text class="muted specs">{{ item.size }} · {{ item.temperature }} · {{ item.milk }}</text>
          <text class="gold bag-price num">¥{{ item.unitPrice * item.quantity }}</text>
        </view>
        <Stepper
          :value="item.quantity"
          :min="0"
          @change="(newValue) => changeQuantity(index, newValue)"
        />
      </view>

      <view v-if="!count" class="bag-empty">
        <Icon name="cup" tone="decor" :size="52" />
        <text class="muted bag-empty-text">购物袋还是空的，选一杯喜欢的咖啡吧。</text>
      </view>

      <template #footer>
        <UiButton
          variant="primary"
          :disabled="!count"
          @tap="bagOpen = false; goToCheckout()"
          style="width: 100%;"
        >
          去结算 ¥{{ subtotal }}
        </UiButton>
      </template>
    </Modal>

    <!--
      取餐凭证弹窗
      入口在「订单」面板的进度卡片内，但弹窗本体留在页面层渲染：
      swiper-item 带 transform，弹窗若作为其后代，position: fixed 会在微信端相对 swiper-item 定位而错位。
    -->
    <view v-if="voucherOpen && order" class="voucher-mask" @tap="voucherOpen = false">
      <view class="voucher" @tap.stop>
        <view class="voucher-heading">
          <text>取餐凭证</text>
          <view
            class="voucher-close"
            aria-label="关闭"
            hover-class="voucher-close--press"
            :hover-stay-time="60"
            @tap="voucherOpen = false"
          >
            <Icon name="close" tone="muted" :size="30" />
          </view>
        </view>
        <text class="voucher-brand">隐白</text>
        <text class="muted voucher-store">{{ storeName }}</text>
        <text class="voucher-number gold">{{ order.pickup }}</text>
        <text>共 {{ order.count }} 杯 · 到店自提</text>
        <text class="muted voucher-hint">请向咖啡师出示此取餐号</text>
        <view class="voucher-action">
          <UiButton variant="primary" @tap="voucherOpen = false" style="width: 100%;">
            收好凭证
          </UiButton>
        </view>
      </view>
    </view>

    <!--
      开屏动画：冷启动第一次进首页才放（consumeSplash 只发一次票）。
      放在页面层而不是独立启动页，首页在它下面就能完成渲染与首屏请求，
      退场时内容已就绪；层级由 $z-splash 保证盖住弹层。
    -->
    <SplashScreen v-if="splashVisible" @done="splashVisible = false" />
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import MainTabBar from '../../components/MainTabBar.vue'
import MenuPanel from '../../components/tabs/MenuPanel.vue'
import OrdersPanel from '../../components/tabs/OrdersPanel.vue'
import MemberPanel from '../../components/tabs/MemberPanel.vue'
import ProfilePanel from '../../components/tabs/ProfilePanel.vue'
import Modal from '../../components/ui/Modal.vue'
import Stepper from '../../components/ui/Stepper.vue'
import UiButton from '../../components/ui/Button.vue'
import SlideConfirm from '../../components/ui/SlideConfirm.vue'
import Icon from '../../components/ui/Icon.vue'
import SplashScreen from '../../components/ui/SplashScreen.vue'
import { shop, count, subtotal, saveCart, completeOrder, cancelOrder, storeInfo } from '../../store/index'
import { consumeSplash } from '../../store/splash'
import { STORE } from '../../config/constants'
import { openPage } from '../../utils/navigation'
import { isLoggedIn } from '../../store/auth'
import { showInfo } from '../../utils/toast'

const TAB_IDS = ['home', 'orders', 'member', 'profile']

const activeIndex = ref(0)
const activeTab = computed(() => TAB_IDS[activeIndex.value])
const bagOpen = ref(false)
const voucherOpen = ref(false)

// 在 setup 里同步取票：早于首帧，不会先闪一下首页再盖上开屏
const splashVisible = ref(consumeSplash())

const order = computed(() => shop.order)
const storeName = STORE.name

const slideConfirm = ref(null)

const menuPanel = ref(null)
const ordersPanel = ref(null)
const memberPanel = ref(null)
const profilePanel = ref(null)

const panelRefs = computed(() => ({
  home: menuPanel.value,
  orders: ordersPanel.value,
  member: memberPanel.value,
  profile: profilePanel.value
}))

onLoad((query) => {
  const index = TAB_IDS.indexOf(query?.tab)
  if (index > 0) activeIndex.value = index
})

onShow(() => {
  activatePanel(activeTab.value)
})

function activatePanel(tab) {
  panelRefs.value[tab]?.activate?.()
}

function switchTo(tab) {
  const index = TAB_IDS.indexOf(tab)
  if (index < 0 || index === activeIndex.value) return
  activeIndex.value = index
  nextTick(() => activatePanel(tab))
}

function onSwiperChange(event) {
  const index = event.detail.current
  if (index === activeIndex.value) return
  activeIndex.value = index
  nextTick(() => activatePanel(TAB_IDS[index]))
}

function changeQuantity(index, newValue) {
  const target = shop.cart[index]
  if (!target) return

  if (newValue <= 0) {
    shop.cart.splice(index, 1)
  } else {
    target.quantity = newValue
  }
  saveCart()
}

function goToCheckout() {
  if (storeInfo.loaded && !storeInfo.open) {
    showInfo('门店已打烊，暂时无法下单')
    return
  }
  if (!isLoggedIn.value) {
    showInfo('请先登录后再下单')
    openPage('auth', { redirect: 'checkout' })
    return
  }
  openPage('checkout')
}

/**
 * 滑动确认胶囊达标后触发。
 * 胶囊本身已锁定为完成态，这里只在失败时解锁重试，成功则随订单移入历史而卸载。
 */
async function completeCurrentOrder() {
  try {
    await completeOrder()
  } catch (error) {
    showInfo(error.message || '完成取餐失败')
    slideConfirm.value?.settle(true)
  }
}

/**
 * 取消当前订单。
 *
 * 已支付的订单不可自行取消（后端会拦），按钮隐藏避免给出无法兑现的选项；
 * 未支付订单给出二次确认，防止误触丢失订单。
 */
function confirmCancelOrder() {
  if (order.value?.status === 'in-progress') {
    showInfo('订单已支付，请联系门店协助取消与退款')
    return
  }
  uni.showModal({
    title: '取消订单',
    content: '确定要取消这笔订单吗？取消后可以重新下单。',
    confirmText: '确定取消',
    cancelText: '再想想',
    // 原生 modal 只接受字符串色值，这里等于设计令牌 $c-gold-text
    confirmColor: '#8a6a14',
    success: async ({ confirm }) => {
      if (!confirm) return
      try {
        await cancelOrder()
        showInfo('订单已取消')
      } catch (error) {
        showInfo(error.message || '取消订单失败')
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.shell-screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
}

/* 顶栏不参与伸缩（原 .shell-screen > view:first-child：小程序端首个子节点编译为
   phone-header 而非 view，故该规则在微信端从未生效，只在 H5 端命中） */
.screen-header {
  flex: 0 0 auto;
}

.tab-swiper {
  flex: 1 1 auto;
  height: 0;
  min-height: 0;
}

.shell-footer {
  padding-bottom: 0;
  box-sizing: border-box;
  background: $c-bg;
  box-shadow: none;
}

/* ---- 购物车条 ---- */
/* 切到首页时轻微上浮入场：底栏是固定层，直接出现会有「闪一块」的跳变感 */
.cart-bar {
  padding: $sp-3 $gutter $sp-3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  animation: bar-in $dur-base $ease-out both;
}

@keyframes bar-in {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cart-copy {
  display: block;
  min-width: 0;
}

.cart-caption {
  display: block;
  font-size: $fs-micro;
}

/* 数量变化时跳一下：让「加了/减了」在底栏也有反馈 */
.cart-count {
  display: inline-block;
  color: $c-ink;
  font-weight: 600;
  animation: number-pop $dur-fast $ease-out both;
}

.cart-total {
  display: block;
  margin-top: $sp-0;
  font-size: $fs-h2;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: $ls-tight;
  @include numeric;
}

.cart-total::before {
  content: '¥';
  margin-right: 2rpx;
  font-size: $fs-caption;
}

/* ---- 订单 Tab 的底栏 ---- */
.order-hint {
  display: block;
  text-align: center;
  font-size: $fs-micro;
  margin: $sp-2 $gutter 0;
}

.order-footer-actions {
  margin: $sp-3 $gutter $sp-3;
}

/* 取消订单入口：弱化为文字链，避免与滑动确认的主操作争夺视觉权重 */
.order-cancel-row {
  display: flex;
  justify-content: center;
  margin: 0 $gutter $sp-3;
}

.order-cancel-link {
  padding: $sp-2 $sp-4;
  color: $c-ink-faint;
  font-size: $fs-body-sm;
  transition: color $dur-fast ease;
}

.order-cancel-link--press {
  color: $c-ink-muted;
}

/* ---- 购物袋 ---- */
.bag-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  padding: $sp-4 0;
  border-top: 1rpx solid $c-divider;
}

.bag-copy {
  min-width: 0;
  flex: 1;
}

.bag-price {
  display: block;
  margin-top: $sp-1;
  font-size: $fs-body-sm;
}

.bag-empty {
  display: flex;
  padding: $sp-6 0;
  flex-direction: column;
  align-items: center;
  gap: $sp-3;
}

.bag-empty-text {
  font-size: $fs-caption;
  text-align: center;
}

/* ---- 取餐凭证弹窗（入口在订单面板，弹窗本体在页面层） ---- */
.voucher-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: $z-mask;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: $sp-7;
  background: $c-scrim;
  animation: modal-enter 200ms ease both;
}

.voucher {
  width: 100%;
  padding: $sp-7 $sp-6 $sp-6;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-3;
  text-align: center;
}

.voucher-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  margin-bottom: $sp-4;
  color: $c-ink-muted;
  font-size: $fs-body-sm;
}

.voucher-close {
  display: flex;
  width: $tap-min;
  height: $tap-min;
  flex: 0 0 $tap-min;
  margin-right: -$sp-4;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color $dur-fast ease, transform $dur-instant $ease-out;
}

.voucher-close--press {
  background: $c-surface-2;
  transform: scale(0.94);
}

.voucher-brand {
  display: block;
  margin-top: $sp-3;
  color: $c-ink-strong;
  font-family: $font-display;
  font-size: $fs-h1;
  line-height: $lh-display;
  letter-spacing: $ls-wider;
}

.voucher-store {
  display: block;
  font-size: $fs-micro;
}

.voucher-number {
  display: block;
  margin: $sp-6 0;
  font-family: $font-display;
  font-size: $fs-ticket;
  line-height: 1.1;
  letter-spacing: $ls-tight;
  @include numeric;
}

.voucher-hint {
  display: block;
  margin: $sp-5 0;
  font-size: $fs-micro;
}

.voucher-action {
  display: block;
  margin-top: $sp-4;
}

/* #ifdef H5 */
.cart-copy {
  cursor: pointer;
}

.voucher-close:hover {
  background: $c-surface-2;
}

.voucher {
  max-width: 390px;
}
/* #endif */
</style>
