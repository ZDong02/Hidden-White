<template>
  <!--
    轮播里的一屏。
    单独成组件有两个实际收益：
      1. 指示点变化时只有本组件的 props 参与比较，三屏内容不会被反复 patch；
      2. 图片的加载 / 失败状态天然按屏隔离，不必在父组件里维护一份按下标的映射表。

    排版约束（改动前先读）：
      · 卡片高度固定（$hero-card-h），但纵向排布只允许「文案自然堆叠 + 操作行贴底」，
        任何一段都不许参与 flex 收缩 —— 否则行盒被压缩，字会被削掉半截；
      · 文案列的右边界由图片区反推（见 $hero-copy-right），保证眉标、标题、短句、
        操作行共用同一条右边界，图片永远不压字；
      · 眉标 / 短句单行省略，主标题最多两行：三屏文案长短不一，行数不定会让整块高度跳动。
  -->
  <view
    class="hero-slide"
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
/* ---- 组件内布局常量：先定图片区，再由它反推文案列，避免两处各写一个魔法值 ----
   卡片实际宽 = 750 − 页面左右留白(44×2) = 662rpx
   （轮播槽左右各 12rpx 的内边距被 swiper 的等量负边距抵消，首屏仍与栅格对齐）；
   卡片内容宽 = 662 − 左右内边距(36×2) = 590rpx。 */
$hero-card-h: 328rpx; // 卡片高度（与 RecommendCarousel 的 swiper 高度联动）
$hero-media: 206rpx; // 图片区边长
$hero-media-right: 36rpx; // 图片距卡片右边缘
$hero-media-top: 72rpx; // 图片上沿 = 上内边距(32) + 眉标行高(31) + 间距(9)：眉标独占一整行，图片不压字
/* 文案右边界 = 590 − 图片宽(206) − 图片右边距(36) − 呼吸位(16) = 332rpx 可用，
   padding-right = 590 − 332 = 258rpx。（图片左沿 420rpx，文案止于 404rpx） */
$hero-copy-right: 258rpx;

.hero-slide {
  position: relative;
  box-sizing: border-box;
  height: $hero-card-h;
  padding: $sp-5 36rpx 28rpx;
  overflow: hidden;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  background: $c-dark-gradient-enhanced;
  color: $c-on-dark;
  box-shadow: 0 12rpx 28rpx rgba(20, 16, 8, 0.22);
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out;

  /* 右上角金色光晕：只做氛围，不参与布局 */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 0;
    background: radial-gradient(
      circle at 82% 16%,
      rgba(212, 173, 47, 0.14) 0%,
      rgba(212, 173, 47, 0) 62%
    );
    pointer-events: none;
  }

  /* 右下角金色细环：品牌笔触 */
  &::after {
    content: '';
    position: absolute;
    right: -70rpx;
    bottom: -130rpx;
    width: 300rpx;
    height: 300rpx;
    border: 2rpx solid $c-hairline-gold;
    border-radius: 50%;
    box-shadow:
      inset 0 0 0 1rpx rgba(212, 173, 47, 0.1),
      0 0 40rpx rgba(212, 173, 47, 0.1);
    pointer-events: none;
  }
}

.hero-slide--press {
  transform: scale(0.994);
  box-shadow: 0 6rpx 14rpx rgba(20, 16, 8, 0.18);
}

/* 文案列：撑满卡片内容区，纵向 space-between 把余量均分到三段间距里
   （眉标贴顶、操作行贴底，单行标题时不会在底部堆出一大块空白）。
   子元素统一 flex-shrink:0 —— 卡片高度固定，内容一旦超出必须溢出（可被发现），
   绝不允许被 flex 压缩：行盒一缩，字就被削掉半截，这是 v2 轮播截断的直接原因。 */
.hero-copy {
  position: relative;
  z-index: 2;
  display: flex;
  height: 100%;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
}

/* 眉标吃满整行（图片从它下方开始），长文案才不会被截成省略号 */
.hero-eyebrow-row {
  display: flex;
  width: 100%;
  flex-shrink: 0;
  align-items: center;
  gap: $sp-1;
  line-height: $lh-tight;
}

.hero-rule {
  width: 24rpx;
  height: 2rpx;
  flex-shrink: 0;
  background: $c-gold;
}

/* 眉标：小字号靠字重与字距撑可读性；中文的字距过大会被拉散，取 2rpx 一档 */
.hero-eyebrow {
  min-width: 0;
  flex: 1;
  color: $c-gold-on-dark;
  font-size: $fs-micro;
  font-weight: $fw-semibold;
  letter-spacing: $ls-wide;
  line-height: $lh-tight;
  @include ellipsis;
}

/* 主标题 / 短句 / 操作行统一让出图片宽度：三者共用同一条右边界 */
.hero-headline,
.hero-caption,
.hero-foot {
  padding-right: $hero-copy-right;
}

/* 主标题：整屏唯一「大声说话」的地方，宋体展示字 + 紧凑行高。
   48rpx 时 332rpx 的文案列一行吃下 6 个汉字（两屏预置文案都是 6 字，正好单行成形）；
   更长的标题折第二行，两行正好被卡片高度容纳；再长才由 line-clamp 省略。 */
.hero-headline {
  display: -webkit-box;
  flex-shrink: 0;
  width: 100%;
  margin-top: $sp-3;
  overflow: hidden;
  color: $c-on-dark;
  font-family: $font-display;
  font-size: 48rpx;
  font-weight: $fw-medium;
  letter-spacing: $ls-tight;
  line-height: 1.15;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: break-word;
}

/* 短句：与标题同一视觉族、弱一档颜色，单行省略（三屏高度不能跟着文案长短跳） */
.hero-caption {
  display: block;
  flex-shrink: 0;
  width: 100%;
  min-height: 39rpx; // 26rpx × 1.5：文案为空时不塌陷，保住与操作行的间距
  margin-top: $sp-2;
  color: $c-on-dark-muted;
  font-size: $fs-caption;
  letter-spacing: $ls-normal;
  line-height: 1.5;
  @include ellipsis;
}

/* 操作行：链接是动作、价格是信息，左右分列同一基线；贴底保证三屏位置一致 */
.hero-foot {
  display: flex;
  width: 100%;
  flex-shrink: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  padding-top: $sp-3;
}

.hero-link-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: $sp-0;
  transition: transform $dur-fast $ease-out;
}

.hero-link {
  color: $c-gold-on-dark;
  font-size: $fs-caption;
  letter-spacing: $ls-normal;
  line-height: 1.5;
  @include ellipsis;
}

/* 价格：等宽数字 + 唯一的大字重，与标题构成第二层级 */
.hero-price {
  flex-shrink: 0;
  color: $c-gold-on-dark;
  font-size: $fs-title;
  font-weight: $fw-semibold;
  letter-spacing: $ls-tight;
  line-height: 1.2;
  @include numeric;
}

/* 图片：上沿与主标题对齐、下沿与操作行对齐，构成右侧一块完整的方形内容区 */
.hero-media {
  position: absolute;
  top: $hero-media-top;
  right: $hero-media-right;
  display: flex;
  width: $hero-media;
  height: $hero-media;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1rpx solid $c-hairline-gold;
  border-radius: $r-md;
  background: linear-gradient(150deg, $c-dark-2, $c-dark-deep);
  box-shadow:
    inset 0 0 0 1rpx rgba(212, 173, 47, 0.08),
    0 8rpx 24rpx rgba(11, 10, 7, 0.4);
  z-index: 1;

  /* 边缘渐隐：图与卡面之间不留一条硬边 */
  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 2;
    border-radius: $r-md;
    background: radial-gradient(
      ellipse at center,
      rgba(11, 10, 7, 0) 46%,
      $c-media-vignette 100%
    );
    pointer-events: none;
  }
}

.hero-media-fallback {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.45;
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
  box-shadow: 0 18rpx 40rpx rgba(20, 16, 8, 0.28);
}

.hero-slide:hover .hero-image {
  transform: scale(1.04);
}

.hero-slide:hover .hero-link-row {
  transform: translateX(4rpx);
}

.hero-slide--press:hover {
  transform: scale(0.994);
}

.hero-slide--press .hero-image {
  transform: scale(1.02);
}
/* #endif */

/* 窄屏（≤340px）不需要单独适配：全部尺寸用 rpx，随视口等比缩放，
   卡片内的比例关系（文案列 : 图片 : 留白）在任何机型上都保持不变。 */
</style>
