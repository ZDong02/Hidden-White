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

        <!--
          身份权益行：会员折扣或首单礼遇，服务端只会给其中一条（两者互斥取优）。
          文案与金额都由服务端下发，前端不参与任何乘法 —— 折扣率换算成「8.5 折」
          这种事各端各写一遍，迟早出现一处 8.5 折、一处 85 折。
        -->
        <view v-for="line in benefitLines" :key="line.type" class="bill-row">
          <text class="gold">{{ line.label }}</text>
          <text class="gold num">−¥{{ line.amount }}</text>
        </view>

        <!-- 优惠券单独一行：它是本页唯一可以更换的优惠，所以做成入口 -->
        <view class="bill-row bill-row--action" hover-class="bill-row--press" :hover-stay-time="60" @tap="openCouponPicker">
          <text class="gold">优惠券</text>
          <view class="bill-value">
            <text v-if="appliedCoupon" class="gold num">−¥{{ appliedCoupon.amount }}</text>
            <text v-else class="muted">{{ usableCount ? `${usableCount} 张可用` : '未使用' }}</text>
            <Icon name="chevron-right" tone="faint" :size="22" />
          </view>
        </view>

        <!--
          免费升杯：必须显式勾选。

          免费额度是稀缺资源（黄金每月 1 次），自动消耗会让顾客在「其实不需要大杯」
          的那一单里被悄悄扣掉，事后在会员页看到「本月已用完」完全想不起来什么时候用的。
          勾选后商品金额按大杯抬高、账单多一行「免费升杯 −X」，合计不变。
        -->
        <view
          class="bill-row bill-row--action upsize-row"
          :class="{ 'upsize-row--disabled': !upsizeSelectable }"
          hover-class="bill-row--press"
          :hover-stay-time="60"
          @tap="toggleUpsize"
        >
          <view class="upsize-copy">
            <text :class="upsizeSelectable ? 'gold' : 'muted'">免费升杯</text>
            <text class="upsize-hint">{{ upsizeHint }}</text>
          </view>
          <view class="bill-value">
            <text v-if="upsizeApplied" class="gold num">−¥{{ tidy(upsizeInfo.sizeDiff) }}</text>
            <view
              v-if="upsizeSelectable"
              :class="['upsize-switch', { 'upsize-switch--on': upsizeApplied }]"
            >
              <view class="upsize-knob" />
            </view>
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

    <!-- 选券弹层：券的可用性随订单金额变化，因此每次打开都按当前金额重算 -->
    <Modal v-model:visible="couponOpen" title="选择优惠券" :mask-closable="true">
      <view v-if="pickerLoading" class="picker-hint">
        <text class="muted">正在加载优惠券…</text>
      </view>

      <template v-else>
        <view
          :class="['picker-row', { 'picker-row--on': !selectedCouponId }]"
          hover-class="picker-row--press"
          :hover-stay-time="60"
          @tap="chooseCoupon('')"
        >
          <view class="picker-main">
            <text class="picker-title">不使用优惠券</text>
            <text class="picker-note">把券留到下次再用</text>
          </view>
          <view v-if="!selectedCouponId" class="picker-check">
            <Icon name="check" tone="gold" :size="22" />
          </view>
        </view>

        <view
          v-for="row in usableList"
          :key="row.id"
          :class="['picker-row', { 'picker-row--on': selectedCouponId === row.id }]"
          hover-class="picker-row--press"
          :hover-stay-time="60"
          @tap="chooseCoupon(row.id)"
        >
          <view class="picker-amount">
            <text class="picker-currency">¥</text>{{ tidy(row.amount) }}
          </view>
          <view class="picker-main">
            <text class="picker-title">{{ row.title }}</text>
            <text class="picker-note">满 {{ tidy(row.minSpend) }} 可用 · {{ row.expiresAt }} 前有效</text>
          </view>
          <view v-if="selectedCouponId === row.id" class="picker-check">
            <Icon name="check" tone="gold" :size="22" />
          </view>
        </view>

        <!-- 不可用的券也要列出来：券包里有、结算页消失，顾客会以为券丢了 -->
        <view v-if="unusableList.length" class="picker-group">
          <text class="picker-group-title">本单不可用</text>
          <view v-for="row in unusableList" :key="row.id" class="picker-row picker-row--off">
            <view class="picker-amount picker-amount--off">
              <text class="picker-currency">¥</text>{{ tidy(row.amount) }}
            </view>
            <view class="picker-main">
              <text class="picker-title">{{ row.title }}</text>
              <text class="picker-note">{{ row.reason }}</text>
            </view>
          </view>
        </view>

        <view v-if="!usableList.length && !unusableList.length" class="picker-hint">
          <text class="muted">还没有优惠券，每周可在券包领一张</text>
        </view>
      </template>

      <template #footer>
        <text class="picker-foot-note">{{ pickerHint }}</text>
      </template>
    </Modal>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import StoreLine from '../../components/StoreLine.vue'
import OrderItem from '../../components/OrderItem.vue'
import Card from '../../components/ui/Card.vue'
import UiButton from '../../components/ui/Button.vue'
import Icon from '../../components/ui/Icon.vue'
import Modal from '../../components/ui/Modal.vue'
import { shop, count, subtotal, total, createOrder, quoteCart } from '../../store/index'
import { loadUsableCoupons } from '../../store/coupons'
import { STORE, BUSINESS } from '../../config/constants'
import { calculateReadyTime } from '../../utils/datetime'
import { openPage, replacePage } from '../../utils/navigation'
import { isLoggedIn } from '../../store/auth'
import { showError, showConfirm, showPrompt, showInfo } from '../../utils/toast'

const submitting = ref(false)
const displaySubtotal = ref(subtotal.value)
const displayPackagingFee = ref(BUSINESS.packagingFee)
const displayTotal = ref(total.value)
/**
 * 服务端下发的优惠明细（会员折扣 / 首单礼遇 / 优惠券）。
 *
 * <p>账单按它逐行渲染，而不是只显示一个「共减 X 元」——
 * 顾客看到少了 7.6 元却不知道为什么，客诉时也无从解释。
 */
const discountLines = ref([])
/** 服务端实际采用的券 id；选了券但首单更优时为空 */
const appliedCouponId = ref('')
/** 本单可用券张数：不点进弹层也能知道「有几张能用」 */
const usableCount = ref(0)

/** 身份权益行（会员折扣 / 首单礼遇）。券与升杯各走入口行，不在这里重复显示 */
const benefitLines = computed(() =>
  discountLines.value.filter((line) => line.type !== 'COUPON' && line.type !== 'UP_SIZE')
)
const appliedCoupon = computed(() => discountLines.value.find((line) => line.type === 'COUPON') || null)

/**
 * 升杯判定结果，直接取自报价响应。
 *
 * <p>不在前端自己算「还剩几次」：额度、等级、杯型全在服务端，
 * 前端算一遍必然出现「页面说能升、下单被拒」。
 * {@code NO_ELIGIBLE_ITEM} 也只有服务端知道（要看本单是不是已经全是大杯）。
 */
const upsizeInfo = ref({ available: false, sizeDiff: 0, reason: 'NOT_REQUESTED' })
const upsizeSelectable = computed(() => upsizeInfo.value?.available === true)
const upsizeApplied = computed(() => upsizeSelectable.value && shop.useUpsize === true)

/** 开关右侧的一行小字：说清「为什么能/不能升」，不让顾客猜。 */
const upsizeHint = computed(() => {
  const info = upsizeInfo.value
  if (!info) return ''
  if (upsizeApplied.value) {
    return `${info.fromSize || '标准杯'} 已升级为 ${info.toSize || '大杯'}`
  }
  if (info.available) {
    if (info.freeLeft === -1) return `${info.fromSize} → ${info.toSize} · 本月不限次`
    if (info.freeLeft > 0) return `${info.fromSize} → ${info.toSize} · 本月免费剩 ${info.freeLeft} 次`
    return `${info.fromSize} → ${info.toSize} · ${info.pointsCost} 积分`
  }
  switch (info.reason) {
    case 'NO_ELIGIBLE_ITEM':
      return '本单已是最大杯型'
    case 'LIMIT_REACHED':
      return '本月次数已用完'
    case 'NO_POINTS_COST':
      return '本月免费次数已用完'
    case 'INSUFFICIENT_POINTS':
      return `积分不足（需 ${info.pointsCost} 分）`
    case 'SIZE_MISSING':
    case 'DISABLED':
    case 'NO_POLICY':
      return '当前不可用'
    default:
      return ''
  }
})

/** 切换升杯并重新报价：金额与杯型必须由服务端重算，前端不做本地加減。 */
async function toggleUpsize() {
  if (!upsizeSelectable.value) return
  shop.useUpsize = !shop.useUpsize
  try {
    await refreshQuote(selectedCouponId.value)
    // 报价失败时把开关退回去，避免界面显示「已升杯」而下单按原价成交
  } catch (error) {
    shop.useUpsize = !shop.useUpsize
    if (!isLoggedIn.value) return
    showError(error.message || '升杯报价失败')
    return
  }
  if (shop.useUpsize) {
    showInfo(upsizeInfo.value.payType === 'FREE' ? '已使用本月免费升杯' : `已使用 ${upsizeInfo.value.pointsCost} 积分升杯`)
  }
}

const couponOpen = ref(false)
const pickerLoading = ref(false)
/** 用户选中的券 id，空串表示不用券 */
const selectedCouponId = ref('')
const usableList = ref([])
const unusableList = ref([])

const storeAddress = STORE.address
const readyTime = calculateReadyTime(BUSINESS.preparationTime)

/**
 * 弹层底部说明。
 *
 * <p>「选了券却没减钱」必须解释清楚：本单首单礼遇与券面额相等时系统走首单礼遇，
 * 券原样保留。不说明的话，顾客会以为券被吞了。
 */
const pickerHint = computed(() => {
  if (selectedCouponId.value && !appliedCouponId.value) {
    return '本单首单礼遇更划算，这张券已为你保留'
  }
  if (appliedCouponId.value) return '优惠券已计入本单，下单后即核销'
  return '每周可领一张，单笔订单限用一张'
})

onShow(async () => {
  if (!isLoggedIn.value) {
    showError('请先登录后再下单')
    openPage('auth', { redirect: 'checkout' })
    return
  }

  try {
    await refreshQuote(selectedCouponId.value)
  } catch (error) {
    // 会话失效（401）：统一处理器已提示，这里直接放弃本轮报价，
    // 否则会再叠一条「获取订单报价失败」，让人以为是价格算错了
    if (!isLoggedIn.value) return
    // 券可能在结算期间失效（过期 / 在别处用掉）：清掉选择再报一次价，别让顾客卡在错误里
    if (selectedCouponId.value) {
      selectedCouponId.value = ''
      try {
        await refreshQuote('')
      } catch (fallbackError) {
        showError(fallbackError.message || '获取订单报价失败')
        return
      }
      showInfo('所选优惠券已失效，已为你取消使用')
      return
    }
    showError(error.message || '获取订单报价失败')
  }
})

/** 报价并回写账单；couponId 为空即不用券。 */
async function refreshQuote(couponId) {
  const quote = await quoteCart(couponId)
  // 报价接口的字段一律以数字展示；任一字段缺失时退回 0，
  // 否则模板会渲染出「¥NaN」，顾客无从判断该付多少
  displaySubtotal.value = toAmount(quote.subtotal)
  displayPackagingFee.value = toAmount(quote.packagingFee)
  displayTotal.value = toAmount(quote.total)
  appliedCouponId.value = quote.couponId || ''

  discountLines.value = (Array.isArray(quote.discounts) ? quote.discounts : []).map(line => ({
    type: String(line.type || ''),
    label: String(line.label || ''),
    amount: toAmount(line.amount)
  }))

  upsizeInfo.value = quote.upsize || { available: false, sizeDiff: 0, reason: 'NOT_REQUESTED' }
  // 服务端判定不可用时必须把开关退回去：否则界面显示「已升杯」，
  // 下单时会被 assertUpsizeUsable 拒成 409，顾客只看到一句莫名其妙的报错
  if (!upsizeInfo.value.available) shop.useUpsize = false
  // 兜底：万一拿到的是没有 discounts 的旧响应，用汇总字段凑一行，
  // 账单不能出现「减了钱但一行明细都没有」
  if (!discountLines.value.length && toAmount(quote.discount) > 0) {
    discountLines.value = [{
      type: quote.couponId ? 'COUPON' : 'FIRST_ORDER',
      label: quote.discountLabel || '优惠',
      amount: toAmount(quote.discount)
    }]
  }
}

/** 金额兜底：非数字（含 undefined / null）一律按 0 处理 */
function toAmount(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

/** 面额展示去掉无意义尾零：6.00 → 6 */
function tidy(value) {
  const num = Number(value)
  return Number.isFinite(num) ? String(Number(num.toFixed(2))) : '0'
}

/**
 * 打开选券弹层。
 *
 * <p>可用性按「当前商品金额」现算，不复用券包里那份快照：
 * 顾客可能刚在购物袋里减掉一杯，券的门槛判定必须跟着变。
 */
async function openCouponPicker() {
  couponOpen.value = true
  pickerLoading.value = true
  try {
    const data = await loadUsableCoupons(displaySubtotal.value)
    usableList.value = Array.isArray(data?.usable) ? data.usable : []
    unusableList.value = Array.isArray(data?.unusable) ? data.unusable : []
    usableCount.value = Number(data?.usableCount) || 0
  } catch (error) {
    usableList.value = []
    unusableList.value = []
    showError(error.message || '优惠券加载失败')
  } finally {
    pickerLoading.value = false
  }
}

/**
 * 选中一张券（空串 = 不用券）。
 *
 * <p>选择先落到本地再报价，报价失败就回滚选择 ——
 * 否则会出现「弹层显示已选、账单没减、下单还带着这张券」的三方不一致。
 */
async function chooseCoupon(couponId) {
  const next = couponId || ''
  if (next === selectedCouponId.value) {
    couponOpen.value = false
    return
  }
  const previous = selectedCouponId.value
  selectedCouponId.value = next
  try {
    await refreshQuote(next)
    couponOpen.value = false
  } catch (error) {
    selectedCouponId.value = previous
    showError(error.message || '该优惠券本单不可用')
  }
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
    // 双保险：报价与下单必须带同一张券，否则会出现「按券算的价、下单没带券」
    shop.couponId = selectedCouponId.value
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

/* 升杯行：左侧标题 + 一行说明，右侧金额 + 开关。
   说明文字单独占一行，是因为「标准杯 → 大杯 · 本月免费剩 1 次」塞不进标题行。 */
.upsize-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2rpx;
}

.upsize-hint {
  color: $c-ink-faint;
  font-size: $fs-micro;
  line-height: 1.5;
}

/* 不可用时不隐藏，只降透明度：仍要能点到，点一下没反应也比整行消失能解释 */
.upsize-row--disabled {
  opacity: 0.55;
}

.upsize-switch {
  position: relative;
  width: 64rpx;
  height: 36rpx;
  flex-shrink: 0;
  border-radius: $r-pill;
  background: $c-border-strong;
  transition: background-color $dur-fast $ease-out;
}

.upsize-switch--on {
  background: $c-gold;
}

.upsize-knob {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: $r-pill;
  background: $c-surface;
  transition: transform $dur-fast $ease-out;
}

.upsize-switch--on .upsize-knob {
  transform: translateX(28rpx);
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

/* ---- 选券弹层 ---- */
.picker-hint {
  padding: $sp-5 0;
  font-size: $fs-caption;
  text-align: center;
}

.picker-row {
  display: flex;
  align-items: center;
  gap: $sp-4;
  min-height: $tap-min;
  padding: $sp-3 $sp-4;
  margin-bottom: $sp-2;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  transition: background-color $dur-fast ease;
}

.picker-row--on {
  border-color: $c-gold-line;
  background: $c-gold-soft;
}

.picker-row--press {
  background: $c-surface-2;
}

.picker-row--off {
  background: $c-surface-2;
}

.picker-amount {
  display: flex;
  min-width: 96rpx;
  flex-shrink: 0;
  align-items: baseline;
  color: $c-gold-text;
  font-family: $font-number;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.picker-amount--off {
  color: $c-ink-faint;
}

.picker-currency {
  font-size: $fs-micro;
  font-weight: 600;
}

.picker-main {
  min-width: 0;
  flex: 1;
}

.picker-title {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: 600;
  @include ellipsis;
}

.picker-row--off .picker-title {
  color: $c-ink-muted;
  font-weight: 500;
}

.picker-note {
  display: block;
  margin-top: $sp-0;
  color: $c-ink-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
}

.picker-check {
  display: flex;
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $c-gold-soft;
}

.picker-group {
  margin-top: $sp-3;
}

.picker-group-title {
  display: block;
  margin-bottom: $sp-2;
  color: $c-ink-muted;
  font-size: $fs-micro;
}

.picker-foot-note {
  display: block;
  color: $c-ink-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
  text-align: center;
}

/* #ifdef H5 */
.bill-row--action:hover {
  background: $c-surface-2;
}
/* #endif */
</style>
