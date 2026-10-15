<template>
  <view class="order-product">
    <image :src="item.detailImage || item.image" mode="aspectFill" />
    <view>
      <text class="product-name">{{ item.name }}</text>
      <text class="muted specs">{{ item.size }} · {{ item.temperature }} · {{ item.milk }}</text>
    </view>
  </view>
  <view class="price-line gold">
    <text>¥{{ item.unitPrice }} / 杯 × {{ item.quantity }}</text>
    <text>¥{{ item.unitPrice * item.quantity }}</text>
  </view>
</template>

<script setup>
defineProps({
  item: {
    type: Object,
    required: true
  }
})
</script>
