<template>
  <view class="screen checkout-screen">
    <PhoneHeader title="确认订单" />

    <view class="checkout-body">
      <!-- 自提信息卡片 - 使用新的 Card 组件 -->
      <Card variant="default" class="pickup-card">
        <view class="pickup-heading">
          <text class="muted">到店自提</text>
          <text>预计 {{ readyTime }} 可取</text>
        </view>
        <StoreLine />
        <text class="address muted">{{ storeAddress }}</text>
        <view class="recipient">
          <text class="muted">取餐方式</text>
          <text>到店后出示取餐号</text>
        </view>
      </Card>

      <!-- 商品列表 -->
      <text class="section-title">本次甄选</text>
      <view v-for="(item, index) in shop.cart" :key="index" class="checkout-item">
        <OrderItem :item="item" />
      </view>

      <!-- 账单明细 -->
      <view class="bill">
        <view class="bill-row">
          <text class="muted">商品金额</text>
          <text class="gold">¥{{ displaySubtotal }}</text>
        </view>
        <view class="bill-row gold" @tap="showOffer">
          <text>会员礼遇</text>
          <view>
            {{ displayDiscount ? `首单礼遇 −¥${displayDiscount}` : '暂无可用优惠' }}
            <text class="bill-arrow">›</text>
          </view>
        </view>
        <view class="bill-row">
          <text class="muted">包装费</text>
          <text class="gold">¥{{ displayPackagingFee }}</text>
        </view>
        <view class="bill-row sum-row">
          <text class="muted">合计</text>
          <text class="gold">¥{{ displayTotal }}</text>
        </view>
      </view>

      <!-- 支付方式 -->
      <view class="payment">
        <view class="wallet-icon">
          <image class="wallet-icon-image" :src="walletIcon" />
        </view>
        <view>
          <text class="payment-name">微信支付 · 演示</text>
          <text class="muted payment-caption">当前为体验下单，不会实际扣款</text>
        </view>
        <text class="checked gold">✓</text>
      </view>

      <!-- 订单备注 -->
      <view class="note-row" @tap="editNote">
        <text class="muted">订单备注</text>
        <view>{{ shop.note || '无需餐具' }} <text class="note-arrow">›</text></view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-fixed checkout-footer">
      <view class="checkout-total">
        <text class="muted">共 {{ count }} 杯 · 实付</text>
        <text class="gold">¥{{ displayTotal }}</text>
      </view>
      <!-- 使用新的 Button 组件 -->
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
import { shop, count, subtotal, total, discount, createOrder, quoteCart } from '../../store/index'
import { STORE, BUSINESS, IMAGES } from '../../config/constants'
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
const walletIcon = IMAGES.icons.wallet

onShow(async () => {
  if (!isLoggedIn.value) {
    showError('请先登录后再下单')
    openPage('auth', { redirect: 'checkout' })
    return
  }

  try {
    const quote = await quoteCart()
    displaySubtotal.value = Number(quote.subtotal)
    displayDiscount.value = Number(quote.discount)
    displayPackagingFee.value = Number(quote.packagingFee)
    displayTotal.value = Number(quote.total)
  } catch (error) {
    showError(error.message || '获取订单报价失败')
  }
})

async function showOffer() {
  await showConfirm({
    title: '首单礼遇',
    content: displayDiscount.value
      ? `首单享 ¥${displayDiscount.value} 立减，已自动计入本次订单。`
      : '首单优惠已使用，感谢继续支持隐白。',
    cancelText: ''
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
  padding-bottom: 330rpx;
}

.checkout-body {
  padding: $sp-6 $gutter 0;
}

.pickup-card {
  margin-bottom: $sp-7;
  background: $c-surface-2;
}

.pickup-heading {
  display: flex;
  justify-content: space-between;
  gap: $sp-4;
  font-size: 24rpx;
  margin-bottom: $sp-6;
}

.address {
  display: block;
  font-size: 24rpx;
  margin-top: $sp-5;
}

.recipient {
  display: flex;
  justify-content: space-between;
  gap: $sp-4;
  margin-top: $sp-6;
  font-size: 26rpx;
}

.checkout-item {
  margin-bottom: $sp-5;
}

.bill {
  margin-top: $sp-7;
  padding: $sp-4 0 $sp-5;
  border-top: 1rpx solid $c-divider;
  border-bottom: 1rpx solid $c-divider;
}

.bill-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  padding: $sp-4 0;
  font-size: 26rpx;
}

.bill-arrow {
  font-size: 36rpx;
  color: $c-ink-faint;
  margin-left: $sp-4;
}

.sum-row {
  min-height: 62rpx;
}

.sum-row .gold {
  font-size: $fs-title-lg;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.payment {
  display: flex;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  padding: $sp-6 0;
}

.wallet-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: $r-xs;
  background: $c-surface-2;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-shrink: 0;
}

/* 支付图标尺寸（原 .wallet-icon image，组件/页面均改用 class） */
.wallet-icon-image {
  width: 38rpx;
  height: 38rpx;
}

.payment-name {
  display: block;
  font-size: 26rpx;
  color: $c-ink-strong;
}

.payment-caption {
  display: block;
  font-size: 22rpx;
  margin-top: $sp-1;
}

.checked {
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  border: 3rpx solid $c-gold-line;
  border-radius: 50%;
  color: $c-gold-text;
  font-size: 26rpx;
  line-height: 1;
}

.note-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  font-size: 26rpx;
  min-height: $tap-min;
  padding: $sp-5 0 $sp-6;
}

/* 备注行右侧的箭头（原 .note-row view text） */
.note-arrow {
  font-size: 30rpx;
  color: $c-ink-faint;
}

.checkout-footer {
  border-top: 1rpx solid $c-divider;
  padding: $sp-4 $gutter 0;
}

.checkout-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $sp-3;
  font-size: 24rpx;
}

.checkout-total .gold {
  font-size: $fs-h2;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* #ifdef H5 */
.note-row:hover .note-arrow {
  color: $c-ink;
}
/* #endif */
</style>
