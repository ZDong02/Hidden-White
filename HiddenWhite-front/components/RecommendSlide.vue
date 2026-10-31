<template>
  <!--
    轮播里的一屏。
    单独成组件有两个实际收益：
      1. 指示点变化时只有本组件的 props 参与比较，三屏内容不会被反复 patch；
      2. 图片的加载 / 失败状态天然按屏隔离，不必在父组件里维护一份按下标的映射表。
  -->
  <view
    class="hero-slide card-dark"
    hover-class="hero-slide--press"
    :hover-stay-time="60"
    :aria-label="label"
    @tap="$emit('select', slide.productId)"
  >
    <view class="hero-copy">
      <view class="hero-eyebrow-row">
        <view class="hero-rule" />
        <text class="hero-eyebrow">{{ slide.eyebrow }}</text>
      </view>
      <text class="hero-headline">{{ slide.headline }}</text>
      <text class="hero-caption">{{ slide.caption }}</text>
      <view class="hero-foot">
        <view class="hero-link-row">
          <text class="hero-link">{{ slide.linkText }}</text>
          <Icon name="arrow-up-right" tone="gold-on-dark" :size="24" />
        </view>
        <text class="hero-price num">¥{{ slide.price }}</text>
      </view>
    </view>

    <view class="hero-media">
      <!-- 占位始终在最底层：图没来、图挂了、图还在解码时，这里都是「品牌色 + 咖啡杯」而不是空白框 -->
      <view class="hero-media-fallback">
        <Icon name="cup" tone="decor" :size="52" />
      </view>
      <!-- eager 为假时根本不挂 src：小程序 image 只要拿到 src 就会开始下载，懒加载必须卡在这里 -->
      <image
        v-if="src"
        class="hero-image"
        :src="src"
        mode="aspectFill"
        @error="onError"
      />
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Icon from './ui/Icon.vue'

const props = defineProps({
  /** 一屏推荐（字段由 store 归一化，文案保证非空） */
  slide: {
    type: Object,
    required: true
  },
  /** 是否允许加载图片：当前屏、相邻屏、或用户已开始滑动时为真 */
  eager: {
    type: Boolean,
    default: false
  }
})

defineEmits(['select'])

/** 网络图失败后重试一次用的地址（带时间戳绕开失败缓存） */
const retrySrc = ref('')
const failed = ref(false)

const src = computed(() => {
  if (!props.eager || failed.value) return ''
  return retrySrc.value || props.slide.image || ''
})

const label = computed(() => `${props.slide.headline}，${props.slide.price} 元，查看详情`)

// 轮播池刷新后这一屏可能换成另一款商品：重置加载状态，
// 否则上一张图的失败标记会粘在新图上，新图永远不显示
watch(
  () => props.slide.image,
  () => {
    retrySrc.value = ''
    failed.value = false
  }
)

/**
 * 加载失败。
 *
 * 网络图先静默重试一次（弱网下第一次失败往往只是抖动），
 * 仍失败就停在占位图上 —— 不弹提示、不留裂图：
 * 轮播是首屏第一眼，为一个次要元素弹 toast 反而打断用户。
 * 包内静态图（/static/**）失败说明资源确实缺了，重试没有意义，直接兜底。
 */
function onError() {
  const url = props.slide.image || ''
  if (!retrySrc.value && /^https?:/i.test(url)) {
    retrySrc.value = `${url}${url.includes('?') ? '&' : '?'}_retry=${Date.now()}`
    return
  }
  failed.value = true
}
</script>

<style lang="scss" scoped>
/* 卡片高度固定：三屏文案长短不一，高度随内容走会导致轮播切换时整块抖动 */
.hero-slide {
  position: relative;
  box-sizing: border-box;
  height: 360rpx;
  padding: $sp-6 $sp-5;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out;
}

.hero-slide--press {
  transform: scale(0.985);
}

/* 文案列：整列不统一让出图片宽度 ——
   眉标横在图片上沿之上，可以吃满整行；只有标题/短句/操作行需要避开图片。
   这样「本周推荐 · 匠心双份浓缩」这类长眉标不会被截成省略号。 */
.hero-copy {
  position: relative;
  z-index: 1;
  display: flex;
  height: 100%;
  flex-direction: column;
}

/* 标题 / 短句 / 操作行避开右侧图片（图片宽 268rpx + 右边距 26rpx + 呼吸位） */
.hero-headline,
.hero-caption,
.hero-foot {
  padding-right: 300rpx;
}

.hero-eyebrow-row {
  display: flex;
  align-items: center;
  gap: $sp-2;
}

.hero-rule {
  width: 26rpx;
  height: 2rpx;
  flex-shrink: 0;
  background: $c-gold;
}

.hero-eyebrow {
  font-size: $fs-micro;
  line-height: $lh-tight;
  letter-spacing: $ls-wide;
  color: $c-gold-on-dark;
  @include ellipsis;
}

/* 主标题用展示字（宋体系），是这一屏唯一的「大声说话」的地方 */
.hero-headline {
  display: block;
  margin-top: $sp-5;
  font-family: $font-display;
  font-size: $fs-h2;
  line-height: $lh-display;
  color: $c-on-dark;
  @include ellipsis;
}

.hero-caption {
  display: block;
  margin-top: $sp-4;
  font-size: $fs-micro;
  line-height: 1.5;
  color: $c-on-dark-muted;
  @include ellipsis;
}

/* 价格与链接同一基线：链接是动作，价格是信息，左右分列读起来不抢 */
.hero-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-2;
  margin-top: $sp-5;
}

.hero-link-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: $sp-1;
}

.hero-link {
  font-size: $fs-micro;
  letter-spacing: 1rpx;
  color: $c-gold-on-dark;
  @include ellipsis;
}

.hero-price {
  flex-shrink: 0;
  font-size: $fs-body;
  font-weight: 600;
  letter-spacing: $ls-tight;
  color: $c-gold-on-dark;
}

/* 图片：比原 Banner 略小并下移一点，把顶部让给整行眉标；比例与位置关系保持不变 */
.hero-media {
  position: absolute;
  right: 26rpx;
  top: 52rpx;
  display: flex;
  width: 268rpx;
  height: 268rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1rpx solid $c-hairline-gold;
  border-radius: $r-md;
  background: linear-gradient(150deg, $c-dark-2, $c-dark-deep);
}

.hero-media-fallback {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.5;
}

.hero-image {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  transition: transform $dur-slow $ease-out;
}

/* ---- H5 悬停：桌面端给鼠标一点回应（小程序端没有 hover，用按压态已足够） ---- */
/* #ifdef H5 */
.hero-slide:hover {
  box-shadow: $sh-3;
}

.hero-slide:hover .hero-image {
  transform: scale(1.04);
}

.hero-slide--press:hover {
  transform: scale(0.985);
}
/* #endif */

/* ---- 窄屏（≤340px，如 SE 一代 320pt）：收紧纵向留白，把高度让给文字 ----
   rpx 本身随机型等比缩放，这里只处理「字挤」而不是「比例」：
   纵向内边距各收 8rpx、图片上沿提 8rpx，换来标题行多出约一整行的高 */
@media (max-width: 340px) {
  .hero-slide {
    padding: $sp-5 $sp-5;
  }

  .hero-headline {
    margin-top: $sp-4;
  }

  .hero-caption {
    margin-top: $sp-3;
  }

  .hero-foot {
    margin-top: $sp-4;
  }
}
</style>
