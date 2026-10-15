<template>
  <!-- 壳页面：四个 Tab 面板通过横向 swiper 切换，底部固定分段控件与内容区同步移动 -->
  <view class="screen shell-screen">
    <PhoneHeader />

    <swiper
      class="tab-swiper"
      :current="activeIndex"
      :duration="320"
      easing-function="easeOutCubic"
      @change="onSwiperChange"
    >
      <swiper-item>
        <MenuPanel ref="menuPanel" @switch-tab="switchTo" />
      </swiper-item>
      <swiper-item>
        <OrdersPanel ref="ordersPanel" />
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
        <view @tap="bagOpen = true">
          <text class="cart-caption muted">已选 {{ count }} 杯 · 门店自提</text>
          <text class="cart-total gold">¥{{ subtotal }}</text>
        </view>
        <!-- 使用新的 Button 组件 -->
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
        <view class="order-footer-actions">
          <UiButton variant="outline" @tap="completeCurrentOrder" style="flex: 1;">
            确认已取餐
          </UiButton>
          <UiButton variant="primary" @tap="voucherOpen = true" style="flex: 1; border: 2rpx solid #b99737;">
            查看取餐凭证
          </UiButton>
        </view>
      </template>

      <MainTabBar :active="activeTab" @select="switchTo" />
      <HomeIndicator />
    </view>

    <!-- 购物袋弹窗 - 使用新的 Modal 组件 -->
    <Modal v-model:visible="bagOpen" title="购物袋" :mask-closable="true">
      <view v-for="(item, index) in shop.cart" :key="`${item.id}-${item.size}-${item.temperature}-${item.milk}`" class="bag-item">
        <view>
          <text class="product-name">{{ item.name }}</text>
          <text class="muted specs">{{ item.size }} · {{ item.temperature }} · {{ item.milk }}</text>
          <text class="gold">¥{{ item.unitPrice * item.quantity }}</text>
        </view>
        <!-- 使用新的 Stepper 组件 -->
        <Stepper
          :value="item.quantity"
          :min="0"
          @change="(newValue) => changeQuantity(index, newValue)"
        />
      </view>

      <text v-if="!count" class="muted empty-hint">购物袋还是空的，选一杯喜欢的咖啡吧。</text>

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

    <!-- 取餐凭证弹窗 -->
    <view v-if="voucherOpen && order" class="voucher-mask" @tap="voucherOpen = false">
      <view class="voucher" @tap.stop>
        <view class="voucher-heading">
          <text>取餐凭证</text>
          <text class="sheet-close" @tap="voucherOpen = false">×</text>
        </view>
        <text class="voucher-brand">隐白</text>
        <text class="muted">{{ storeName }}</text>
        <text class="voucher-number gold">{{ order.pickup }}</text>
        <text>共 {{ order.count }} 杯 · 到店自提</text>
        <text class="muted voucher-hint">请向咖啡师出示此取餐号</text>
        <UiButton variant="primary" @tap="voucherOpen = false" style="width: 100%; margin-top: 24rpx;">
          收好凭证
        </UiButton>
      </view>
    </view>
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
import { shop, count, subtotal, saveCart, completeOrder, storeInfo } from '../../store/index'
import { STORE } from '../../config/constants'
import { openPage } from '../../utils/navigation'
import { isLoggedIn } from '../../store/auth'
import { showInfo, showConfirm } from '../../utils/toast'

const TAB_IDS = ['home', 'orders', 'member', 'profile']

const activeIndex = ref(0)
const activeTab = computed(() => TAB_IDS[activeIndex.value])
const bagOpen = ref(false)
const voucherOpen = ref(false)

const order = computed(() => shop.order)
const storeName = STORE.name

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

async function completeCurrentOrder() {
  const confirmed = await showConfirm({
    title: '确认完成取餐',
    content: '确认后订单会移入历史记录。'
  })

  if (confirmed) {
    try {
      voucherOpen.value = false
      await completeOrder()
    } catch (error) {
      showInfo(error.message || '完成取餐失败')
    }
  }
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

.shell-screen > view:first-child {
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

.cart-bar {
  /* 水平 40rpx 与底部分段胶囊对齐，两栏边缘一致；垂直放大增加呼吸感 */
  padding: $sp-3 40rpx $sp-3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
}

.cart-caption {
  display: block;
  font-size: 22rpx;
}

.cart-total {
  display: block;
  font-size: $fs-h2;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: $ls-tight;
  font-variant-numeric: tabular-nums;
  margin-top: 6rpx;
}

.order-hint {
  display: block;
  text-align: center;
  font-size: 22rpx;
  margin: $sp-2 40rpx 0;
}

.order-footer-actions {
  display: flex;
  gap: $sp-4;
  margin: $sp-2 40rpx $sp-3;
}

/* 购物袋内容 */
.bag-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  padding: $sp-4 0;
  border-top: 1rpx solid $c-divider;
}

.bag-item .gold {
  display: block;
  margin-top: $sp-2;
  font-size: 26rpx;
  font-variant-numeric: tabular-nums;
}

.empty-hint {
  display: block;
  text-align: center;
  padding: $sp-5 0;
}

/* 取餐凭证 */
.voucher-mask {
  position: fixed;
  inset: 0;
  background: rgba(18, 14, 7, 0.6);
  z-index: 30;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: $sp-7;
  animation: modal-enter 200ms ease both;
}

.voucher {
  padding: $sp-7 $sp-6 $sp-6;
  width: 100%;
  background: $c-surface;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  text-align: center;
  box-shadow: $sh-3;
  animation: sheet-enter 240ms $ease-out both;
}

.voucher-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  font-size: 26rpx;
  color: $c-ink-muted;
  margin-bottom: $sp-4;
}

.sheet-close {
  display: flex;
  width: $tap-min;
  height: $tap-min;
  flex: 0 0 $tap-min;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: $c-ink-muted;
  font-size: 48rpx;
  line-height: 1;
  transition: background-color $dur-fast ease, color $dur-fast ease;
}

.sheet-close:active {
  background: $c-surface-2;
  color: $c-ink-strong;
}

.voucher-brand {
  display: block;
  font-family: $font-display;
  font-size: 60rpx;
  line-height: $lh-display;
  letter-spacing: $ls-wider;
  color: $c-ink-strong;
  margin-top: $sp-3;
}

.voucher-number {
  display: block;
  font-family: $font-display;
  font-size: $fs-ticket;
  line-height: 1.1;
  letter-spacing: $ls-tight;
  font-variant-numeric: tabular-nums;
  margin: $sp-6 0;
}

.voucher-hint {
  display: block;
  font-size: 22rpx;
  margin: $sp-5 0;
}

/* #ifdef H5 */
.sheet-close:hover {
  background: $c-surface-2;
}

.voucher {
  max-width: 390px;
}
/* #endif */
</style>
