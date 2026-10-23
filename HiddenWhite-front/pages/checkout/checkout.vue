<template>
  <view class="screen checkout-screen">
    <PhoneHeader title="确认订单" />

    <view class="checkout-body">
      <!-- 自提信息卡片 -->
      <Card variant="sunken" class="pickup-card reveal">
        <view class="pickup-heading">
          <text class="muted pickup-label">到店自提</text>
          <text class="pickup-time">预计 {{ readyTime }} 可取</text>
        </view>
        <StoreLine />
        <text class="address muted">{{ storeAddress }}</text>
        <view class="recipient">
          <text class="muted">取餐方式</text>
          <text>到店后出示取餐号</text>
        </view>
      </Card>

      <!-- 商品列表 -->
      <text class="section-title reveal reveal-1">本次甄选</text>
      <view v-for="(item, index) in shop.cart" :key="index" class="checkout-item">
        <OrderItem :item="item" />
      </view>

      <!-- 账单明细 -->
      <view class="bill reveal reveal-2">
        <view class="bill-row">
          <text class="muted">商品金额</text>
          <text class="gold num">¥{{ displaySubtotal }}</text>
        </view>
        <view class="bill-row bill-row--action" hover-class="bill-row--press" :hover-stay-time="60" @tap="showOffer">
          <text class="gold">会员礼遇</text>
          <view class="bill-value">
            <text class="gold">
              {{ displayDiscount ? `首单礼遇 −¥${displayDiscount}` : '暂无可用优惠' }}
            </text>
            <Icon name="chevron-right" tone="gold" :size="22" />
          </view>
        </view>
        <view class="bill-row">
          <text class="muted">包装费</text>
          <text class="gold num">¥{{ displayPackagingFee }}</text>
        </view>
        <view class="bill-row sum-row">
          <text class="muted">合计</text>
          <text class="gold sum-value num">¥{{ displayTotal }}</text>
        </view>
      </view>

      <!-- 支付方式 -->
      <view class="payment reveal reveal-3">
        <view class="wallet-icon">
          <Icon name="wallet" tone="ink" :size="34" />
        </view>
        <view class="payment-copy">
          <text class="payment-name">微信支付 · 演示</text>
          <text class="muted payment-caption">当前为体验下单，不会实际扣款</text>
        </view>
        <view class="checked">
          <Icon name="check" tone="gold" :size="24" />
        </view>
      </view>

      <!-- 订单备注 -->
      <view class="note-row" hover-class="note-row--press" :hover-stay-time="60" @tap="editNote">
        <text class="muted">订单备注</text>
        <view class="note-value">
          <text>{{ shop.note || '无需餐具' }}</text>
          <Icon name="chevron-right" tone="faint" :size="22" />
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-fixed checkout-footer">
      <view class="checkout-total">
        <text class="muted">共 {{ count }} 杯 · 实付</text>
        <text class="gold checkout-amount num">¥{{ displayTotal }}</text>
      </view>
      <UiButton
        variant="primary"
        :disabled="!count || submitting"
        @tap="submit"
        style="width: 100%;"
      >
        {{ submitting ? '正在提交…' : `确认下单 ¥${displayTotal}` }}
      </UiButton>
      <HomeIndicator />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import StoreLine from '../../components/StoreLine.vue'
import OrderItem from '../../components/OrderItem.vue'
import Card from '../../components/ui/Card.vue'
import UiButton from '../../components/ui/Button.vue'
import Icon from '../../components/ui/Icon.vue'
import { shop, count, subtotal, total, discount, createOrder, quoteCart } from '../../store/index'
import { STORE, BUSINESS } from '../../config/constants'
import { calculateReadyTime } from '../../utils/datetime'
import { openPage, replacePage } from '../../utils/navigation'
import { isLoggedIn } from '../../store/auth'
import { showError, showConfirm, showPrompt } from '../../utils/toast'

const submitting = ref(false)
const displaySubtotal = ref(subtotal.value)
const displayDiscount = ref(discount.value)
const displayPackagingFee = ref(BUSINESS.packagingFee)
const displayTotal = ref(total.value)

const storeAddress = STORE.address
const readyTime = calculateReadyTime(BUSINESS.preparationTime)

onShow(async () => {
  if (!isLoggedIn.value) {
    showError('请先登录后再下单')
    openPage('auth', { redirect: 'checkout' })
    return
  }

  try {
    const quote = await quoteCart()
    // 报价接口的字段一律以数字展示；任一字段缺失时退回 0，
    // 否则模板会渲染出「¥NaN」，顾客无从判断该付多少
    displaySubtotal.value = toAmount(quote.subtotal)
    displayDiscount.value = toAmount(quote.discount)
    displayPackagingFee.value = toAmount(quote.packagingFee)
    displayTotal.value = toAmount(quote.total)
  } catch (error) {
    showError(error.message || '获取订单报价失败')
  }
})

/** 金额兜底：非数字（含 undefined / null）一律按 0 处理 */
function toAmount(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

async function showOffer() {
  await showConfirm({
    title: '首单礼遇',
    content: displayDiscount.value
      ? `首单享 ¥${displayDiscount.value} 立减，已自动计入本次订单。`
      : '首单优惠已使用，感谢继续支持隐白。',
    showCancel: false
  })
}

async function editNote() {
  const note = await showPrompt({
    title: '订单备注',
    placeholder: '例如：无需餐具',
    value: shop.note
  })

  if (note !== null) {
    shop.note = note
  }
}

async function submit() {
  if (!isLoggedIn.value) {
    showError('请先登录后再下单')
    openPage('auth', { redirect: 'checkout' })
    return
  }

  if (submitting.value || !count.value) {
    return
  }

  const confirmed = await showConfirm({
    title: '确认下单',
    content: '当前尚未接入微信支付，确认后会创建取餐订单，不会实际扣款。'
  })

  if (!confirmed) {
    return
  }

  try {
    submitting.value = true
    await createOrder()
    replacePage('order')
  } catch (error) {
    showError(error.message || '订单提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.checkout-screen {
  padding-bottom: $pad-page-checkout;
}

.checkout-body {
  padding: $pad-panel-top $gutter 0;
}

.pickup-card {
  margin-bottom: $sp-7;
}

.pickup-heading {
  display: flex;
  justify-content: space-between;
  gap: $sp-4;
  margin-bottom: $sp-4;
  font-size: $fs-caption;
}

.pickup-label {
  letter-spacing: $ls-normal;
}

.pickup-time {
  color: $c-ink-strong;
  font-weight: 600;
}

.address {
  display: block;
  margin-top: $sp-2;
  font-size: $fs-caption;
}

.recipient {
  display: flex;
  justify-content: space-between;
  gap: $sp-4;
  margin-top: $sp-5;
  padding-top: $sp-4;
  border-top: 1rpx solid $c-border;
  font-size: $fs-body-sm;
}

.checkout-item {
  margin-bottom: $sp-5;
}

/* ---- 账单 ---- */
.bill {
  margin-top: $sp-7;
  padding: $sp-2 0 $sp-4;
  border-top: 1rpx solid $c-divider;
  border-bottom: 1rpx solid $c-divider;
}

.bill-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  font-size: $fs-body-sm;
  transition: background-color $dur-fast ease;
}

.bill-row--action {
  margin: 0 (-$sp-3);
  padding: 0 $sp-3;
  border-radius: $r-sm;
}

.bill-row--press {
  background: $c-surface-2;
}

.bill-value {
  display: flex;
  align-items: center;
  gap: $sp-0;
}

.sum-row {
  min-height: 62rpx;
}

.sum-value {
  font-size: $fs-title-lg;
  font-weight: 600;
}

/* ---- 支付方式 ---- */
.payment {
  display: flex;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  padding: $sp-6 0;
}

.wallet-icon {
  display: flex;
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: $r-xs;
  background: $c-surface-2;
}

.payment-copy {
  min-width: 0;
  flex: 1;
}

.payment-name {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
}

.payment-caption {
  display: block;
  margin-top: $sp-0;
  font-size: $fs-micro;
}

/* 已选标记：浅金圆底 + 金色对勾，与选项按钮的选中态同一套语言 */
.checked {
  display: flex;
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $c-gold-soft;
  animation: number-pop 320ms $ease-out both;
}

/* ---- 备注 ---- */
.note-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  padding: $sp-4 0 $sp-6;
  font-size: $fs-body-sm;
  transition: opacity $dur-fast ease;
}

.note-row--press {
  opacity: 0.6;
}

.note-value {
  display: flex;
  align-items: center;
  gap: $sp-1;
  color: $c-ink;
}

/* ---- 底栏 ---- */
.checkout-footer {
  border-top: 1rpx solid $c-divider;
  padding: $sp-4 $gutter 0;
}

.checkout-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $sp-3;
  font-size: $fs-caption;
}

.checkout-amount {
  font-size: $fs-h2;
  font-weight: 600;
}

/* #ifdef H5 */
.bill-row--action:hover {
  background: $c-surface-2;
}
/* #endif */
</style>
