<template>
  <view class="screen detail-screen">
    <PhoneHeader title="咖啡详情" />

    <image class="detail-hero" :src="heroImage" mode="aspectFill" />

    <view class="detail-body">
      <!-- 产品标题 -->
      <view class="detail-title">
        <text>{{ product.name }}</text>
        <text class="gold">¥{{ unitPrice }}</text>
      </view>

      <text class="detail-subtitle muted">{{ product.detailDescription }}</text>
      <text class="description muted">{{ product.description }}</text>

      <view class="divider" />

      <!-- 杯型选择 -->
      <view class="option-section">
        <text class="option-label">杯型</text>
        <view class="option-row">
          <button
            v-for="(option, index) in sizeOptions"
            :key="index"
            :class="['option-button', { selected: sizeIndex === index, premium: index === 1 }]"
            @tap="sizeIndex = index"
          >
            {{ option.label }}
          </button>
        </view>
      </view>

      <!-- 温度选择 -->
      <view class="option-section">
        <text class="option-label">温度</text>
        <view class="option-row">
          <button
            v-for="temp in temperatureOptions"
            :key="temp"
            :class="['option-button', { selected: temperature === temp }]"
            @tap="temperature = temp"
          >
            {{ temp }}
          </button>
        </view>
      </view>

      <!-- 奶类选择（仅限有奶产品） -->
      <view v-if="product.hasMilk" class="option-section">
        <text class="option-label">奶类</text>
        <view class="option-row milk-row">
          <button
            v-for="(option, index) in milkOptions"
            :key="index"
            :class="['option-button', { selected: milkIndex === index, premium: index === 1 }]"
            @tap="milkIndex = index"
          >
            {{ option.label }}
          </button>
        </view>
      </view>

      <!-- 数量选择 - 使用新的 Stepper 组件 -->
      <view class="quantity-row">
        <view>
          <text class="option-label">数量</text>
          <text class="quantity-caption muted">不额外加糖，保留咖啡本味</text>
        </view>
        <Stepper v-model:value="quantity" :min="1" :max="99" />
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-fixed detail-footer">
      <view class="detail-cart">
        <view>
          <text class="muted footer-caption">{{ quantity }} 杯 · 已选{{ product.name }}</text>
          <text class="footer-total gold">¥{{ unitPrice * quantity }}</text>
        </view>
        <!-- 使用新的 Button 组件 -->
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
import { productData } from '../../data/products'
import { OPTIONS } from '../../config/constants'
import { BACKEND_ENABLED, requestApi } from '../../config/backend'
import { navigateBack } from '../../utils/navigation'
import { showInfo, showError } from '../../utils/toast'
import { getProductDetailImage } from '../../utils/image'

const product = ref(productData[0])
const sizeIndex = ref(0)
const temperature = ref('热')
const milkIndex = ref(0)
const quantity = ref(1)

const sizeOptions = ref(OPTIONS.sizes)
const temperatureOptions = ref(OPTIONS.temperatures)
const milkOptions = ref(OPTIONS.milks)

const heroImage = computed(() => getProductDetailImage(product.value))

const unitPrice = computed(() => {
  let price = product.value.price
  price += sizeOptions.value[sizeIndex.value].price
  if (product.value.hasMilk) {
    price += milkOptions.value[milkIndex.value].price
  }
  return price
})

// 规格选项缓存
let optionsCache = null

onLoad(async (query) => {
  if (BACKEND_ENABLED) {
    try {
      const options = optionsCache || await requestApi('/products/options')
      optionsCache = options

      sizeOptions.value = options.sizes.map((item) => ({
        label: item.label,
        price: Number(item.price ?? item.extraPrice ?? 0)
      }))
      temperatureOptions.value = options.temperatures
      milkOptions.value = options.milks.map((item) => ({
        label: item.label,
        price: Number(item.price ?? item.extraPrice ?? 0)
      }))
    } catch (error) {
      showError(error.message || '加载商品规格失败')
    }
  }

  if (query.id) {
    const foundProduct = products.find((p) => p.id === query.id)
    if (foundProduct) {
      product.value = foundProduct
    } else {
      showError('商品不存在或已下架')
      navigateBack()
      return
    }
  }

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
  const item = {
    ...product.value,
    size: sizeOptions.value[sizeIndex.value].label,
    temperature: temperature.value,
    milk: product.value.hasMilk ? milkOptions.value[milkIndex.value].label : '无奶',
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
  padding-bottom: 260rpx;
}

.detail-hero {
  display: block;
  width: 100%;
  height: 398rpx;
  background: $c-surface-3;
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

.detail-title > text:first-child {
  font-family: $font-display;
  font-size: $fs-h2;
  line-height: $lh-display;
  letter-spacing: 0.5rpx;
  color: $c-ink-strong;
}

.detail-title .gold {
  flex-shrink: 0;
  font-size: $fs-title-lg;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.detail-subtitle {
  display: block;
  font-size: $fs-caption;
  margin-top: $sp-3;
}

.description {
  display: block;
  font-size: $fs-body-sm;
  line-height: 1.75;
  margin-top: $sp-2;
  margin-bottom: $sp-4;
}

.option-section {
  margin-top: $sp-5;
}

.option-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: $c-ink-strong;
  margin-bottom: $sp-4;
}

.option-row {
  display: flex;
  gap: $sp-3;
}

.option-button {
  flex: 1;
  height: $tap-min;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: $tap-min;
  padding: 0 $sp-2;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  color: $c-ink;
  font-size: 26rpx;
  transition: background-color 180ms ease, border-color 180ms ease, color 180ms ease, transform 180ms ease, box-shadow 180ms ease;
}

.option-button.premium {
  color: $c-gold-text;
}

.option-button.selected {
  background: $c-gold-soft;
  border: 2rpx solid $c-gold-line;
  color: $c-ink-strong;
  font-weight: 600;
  box-shadow: $sh-1;
}

.milk-row {
  gap: $sp-3;
}

.milk-row .option-button {
  font-size: 24rpx;
}

.quantity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  margin-top: $sp-5;
}

.quantity-caption {
  display: block;
  font-size: 22rpx;
}

.quantity-row .option-label {
  margin-bottom: 6rpx;
}

.option-button:active {
  transform: scale(0.97);
}

.detail-footer {
  border-top: 1rpx solid $c-divider;
}

.detail-cart {
  padding: $sp-5 $gutter $sp-4;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
}

.footer-caption {
  display: block;
  font-size: 22rpx;
}

.footer-total {
  display: block;
  font-size: $fs-h2;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: $ls-tight;
  font-variant-numeric: tabular-nums;
  margin-top: $sp-1;
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
