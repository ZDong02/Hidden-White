<template>
  <!-- 单条商品：图片在左，名称/规格/金额在右，卡片本体统一沿用 .order-product -->
  <view class="order-product">
    <image class="order-product__image" :src="item.detailImage || item.image" mode="aspectFill" />
    <view class="order-product__body">
      <text class="product-name">{{ item.name }}</text>
      <text class="muted specs">{{ item.size }} · {{ item.temperature }} · {{ item.milk }}</text>
      <view class="price-line gold">
        <text>¥{{ item.unitPrice }} / 杯 × {{ item.quantity }}</text>
        <text>¥{{ item.unitPrice * item.quantity }}</text>
      </view>
    </view>
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

<style lang="scss" scoped>
/* 主体样式在 common.scss（跨组件复用），这里只补本组件特有的排布 */
.order-product__body {
  min-width: 0;
  flex: 1;
}

.order-product__image {
  /* 尺寸写在这里而不只依赖 common.scss 的 .order-product image：
     组件样式隔离策略在不同基础库版本下不完全一致，自身兜一层更稳 */
  width: 118rpx;
  height: 118rpx;
  flex: 0 0 118rpx;
  border-radius: $r-sm;
  background: $c-surface-3;
}
</style>
