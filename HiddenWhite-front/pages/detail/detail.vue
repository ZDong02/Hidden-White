<template>
  <!-- 商品由接口异步填入；未加载/已下架时不渲染任何可交互内容 -->
  <view v-if="!product" class="screen detail-screen" />
  <view v-else class="screen detail-screen">
    <PhoneHeader title="咖啡详情" />

    <image class="detail-hero" :src="heroImage" mode="aspectFill" />

    <view class="detail-body">
      <!-- 产品标题 -->
      <view class="detail-title reveal">
        <text class="detail-title-name">{{ product.name }}</text>
        <text class="gold detail-price num">¥{{ unitPrice }}</text>
      </view>

      <text class="detail-subtitle muted reveal reveal-1">{{ product.detailDescription }}</text>
      <text class="description muted reveal reveal-1">{{ product.description }}</text>

      <view class="divider" />

      <!-- 杯型选择 -->
      <view class="option-section reveal reveal-2">
        <text class="option-label">杯型</text>
        <view class="option-row">
          <button
            v-for="(option, index) in sizeOptions"
            :key="index"
            :class="['option-button', { selected: sizeIndex === index }]"
            hover-class="option-button--press"
            :hover-stay-time="60"
            @tap="sizeIndex = index"
          >
            <text class="option-text">{{ option.label }}</text>
            <text v-if="option.price" class="option-price">+¥{{ option.price }}</text>
          </button>
        </view>
      </view>

      <!-- 温度选择 -->
      <view class="option-section reveal reveal-3">
        <text class="option-label">温度</text>
        <view class="option-row">
          <button
            v-for="temp in temperatureOptions"
            :key="temp"
            :class="['option-button', { selected: temperature === temp }]"
            hover-class="option-button--press"
            :hover-stay-time="60"
            @tap="temperature = temp"
          >
            <text class="option-text">{{ temp }}</text>
          </button>
        </view>
      </view>

      <!-- 奶类选择（仅限有奶产品） -->
      <view v-if="product.hasMilk" class="option-section reveal reveal-4">
        <text class="option-label">奶类</text>
        <view class="option-row">
          <button
            v-for="(option, index) in milkOptions"
            :key="index"
            :class="['option-button', { selected: milkIndex === index }]"
            hover-class="option-button--press"
            :hover-stay-time="60"
            @tap="milkIndex = index"
          >
            <text class="option-text">{{ option.label }}</text>
            <text v-if="option.price" class="option-price">+¥{{ option.price }}</text>
          </button>
        </view>
      </view>

      <!-- 数量选择 -->
      <view class="quantity-row reveal reveal-5">
        <view class="quantity-copy">
          <text class="option-label">数量</text>
          <text class="quantity-caption muted">不额外加糖，保留咖啡本味</text>
        </view>
        <Stepper v-model:value="quantity" :min="1" :max="99" />
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-fixed detail-footer">
      <view class="detail-cart">
        <view class="detail-cart-copy">
          <text class="muted footer-caption">{{ quantity }} 杯 · 已选{{ product.name }}</text>
          <text class="footer-total gold">{{ unitPrice * quantity }}</text>
        </view>
        <UiButton variant="primary" @tap="addToBag" style="width: 336rpx; flex-shrink: 0;">
          加入购物袋
        </UiButton>
      </view>
      <HomeIndicator />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import Stepper from '../../components/ui/Stepper.vue'
import UiButton from '../../components/ui/Button.vue'
import { products, shop, addItem } from '../../store/index'
import { OPTIONS, IMAGES } from '../../config/constants'
import { requestApi } from '../../config/backend'
import { navigateBack } from '../../utils/navigation'
import { showInfo, showError } from '../../utils/toast'
import { getProductDetailImage } from '../../utils/image'

// 商品一律来自 /api/products：没有 id 或 id 不存在时直接退回菜单页，
// 不再用本地 mock 商品顶替——那会让顾客对着一个门店并不存在的商品下单。
const product = ref(null)
const sizeIndex = ref(0)
const temperature = ref('热')
const milkIndex = ref(0)
const quantity = ref(1)

const sizeOptions = ref(OPTIONS.sizes)
const temperatureOptions = ref(OPTIONS.temperatures)
const milkOptions = ref(OPTIONS.milks)

const heroImage = computed(() => (product.value ? getProductDetailImage(product.value) : IMAGES.coffee.latte))

// 规格可能为空（管理端把某类选项全部下架）：此时只算商品基础价，
// 不做 `sizeOptions[i].price` 这类必然抛错的下标访问。
const unitPrice = computed(() => {
  if (!product.value) return 0
  let price = Number(product.value.price) || 0
  const size = sizeOptions.value[sizeIndex.value]
  if (size) price += Number(size.price) || 0
  if (product.value.hasMilk) {
    const milk = milkOptions.value[milkIndex.value]
    if (milk) price += Number(milk.price) || 0
  }
  return price
})

// 规格选项缓存
let optionsCache = null

onLoad(async (query) => {
  try {
    const options = optionsCache || await requestApi('/products/options')
    optionsCache = options

    sizeOptions.value = (options.sizes || []).map((item) => ({
      label: item.label,
      price: Number(item.price ?? item.extraPrice ?? 0)
    }))
    temperatureOptions.value = options.temperatures || []
    milkOptions.value = (options.milks || []).map((item) => ({
      label: item.label,
      price: Number(item.price ?? item.extraPrice ?? 0)
    }))
  } catch (error) {
    showError(error.message || '加载商品规格失败')
  }

  const foundProduct = query.id ? products.find((p) => p.id === query.id) : null
  if (!foundProduct) {
    showError(query.id ? '商品不存在或已下架' : '请从菜单选择商品')
    navigateBack()
    return
  }
  product.value = foundProduct

  // 恢复已选规格
  const existingItem = shop.cart.find((item) => item.id === product.value.id)
  if (existingItem) {
    const savedSizeIndex = sizeOptions.value.findIndex((option) => option.label === existingItem.size)
    const savedMilkIndex = milkOptions.value.findIndex((option) => option.label === existingItem.milk)

    if (savedSizeIndex >= 0) sizeIndex.value = savedSizeIndex
    if (temperatureOptions.value.includes(existingItem.temperature)) {
      temperature.value = existingItem.temperature
    }
    if (savedMilkIndex >= 0) milkIndex.value = savedMilkIndex
    quantity.value = existingItem.quantity
  } else {
    quantity.value = 1
  }
})

function addToBag() {
  if (!product.value) return
  const item = {
    ...product.value,
    size: sizeOptions.value[sizeIndex.value]?.label || '',
    temperature: temperature.value,
    milk: product.value.hasMilk ? milkOptions.value[milkIndex.value]?.label || '全脂奶' : '无奶',
    quantity: quantity.value,
    unitPrice: unitPrice.value
  }

  addItem(item, true)
  navigateBack()
  showInfo('已加入购物袋')
}
</script>

<style lang="scss" scoped>
.detail-screen {
  padding-bottom: $pad-page-detail;
}

/* Hero：底部压一层极淡的暖色渐变，让图片与纸白画布之间有过渡而不是硬边 */
.detail-hero {
  display: block;
  width: 100%;
  height: 398rpx;
  background: $c-surface-3;
  animation: screen-enter $dur-slow ease both;
}

.detail-body {
  padding: $sp-7 $gutter $sp-7;
}

.detail-title {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: $sp-4;
}

.detail-title-name {
  font-family: $font-display;
  font-size: $fs-h2;
  line-height: $lh-display;
  letter-spacing: 0.5rpx;
  color: $c-ink-strong;
}

.detail-price {
  flex-shrink: 0;
  font-size: $fs-title-lg;
  font-weight: 600;
}

.detail-subtitle {
  display: block;
  margin-top: $sp-3;
  font-size: $fs-caption;
}

.description {
  display: block;
  margin-top: $sp-2;
  margin-bottom: $sp-4;
  font-size: $fs-body-sm;
  line-height: 1.75;
}

.option-section {
  margin-top: $sp-5;
}

.option-label {
  display: block;
  margin-bottom: $sp-3;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: 600;
}

.option-row {
  display: flex;
  gap: $sp-3;
}

/* 规格按钮：选中不靠改 border-width（会挤动文字），用 inset 阴影画第二道金边 */
.option-button {
  /* flex-basis 必须为 0：否则宽度由文字长短决定，同一行的按钮宽窄不一 */
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  min-height: $tap-min;
  padding: $sp-2 $sp-2;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  color: $c-ink;
  transition: background-color $dur-fast ease, border-color $dur-fast ease,
    color $dur-fast ease, box-shadow $dur-fast ease, transform $dur-instant $ease-out;
}

.option-text {
  font-size: $fs-caption;
  line-height: 1.3;
}

.option-price {
  color: $c-gold-text;
  font-size: $fs-micro;
  line-height: 1.2;
}

.option-button.selected {
  border-color: $c-gold-line;
  background: $c-gold-soft;
  color: $c-ink-strong;
  font-weight: 600;
  box-shadow: inset 0 0 0 1rpx $c-gold-line;
}

.option-button--press {
  transform: scale(0.97);
}

.quantity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  margin-top: $sp-6;
  padding-top: $sp-5;
  border-top: 1rpx solid $c-divider;
}

.quantity-copy {
  min-width: 0;
}

.quantity-row .option-label {
  margin-bottom: $sp-0;
}

.quantity-caption {
  display: block;
  font-size: $fs-micro;
}

/* ---- 底部操作栏 ---- */
.detail-footer {
  border-top: 1rpx solid $c-divider;
}

.detail-cart {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  padding: $sp-4 $gutter;
}

.detail-cart-copy {
  min-width: 0;
}

.footer-caption {
  display: block;
  font-size: $fs-micro;
}

/* 金额前的货币符号降一档字号，数字本身才是重点 */
.footer-total {
  display: block;
  margin-top: $sp-0;
  font-size: $fs-h2;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: $ls-tight;
  @include numeric;
}

.footer-total::before {
  content: '¥';
  margin-right: 2rpx;
  font-size: $fs-caption;
}

/* #ifdef H5 */
.option-button:hover {
  border-color: $c-border-strong;
}

.option-button.selected:hover {
  border-color: $c-gold;
}
/* #endif */
</style>
