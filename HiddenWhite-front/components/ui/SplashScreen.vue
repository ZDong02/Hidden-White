<template>
  <!--
    开屏动画（覆盖层）
    ==================================================================
    为什么做「覆盖层」而不是独立启动页：
      独立页要用 redirectTo 切走，首页的数据请求得等跳转完才开始，
      观众看完 1.6s 动画后还要再等一次接口，等于两次等待叠加。
      覆盖层让首页在动画期间就已经挂载、渲染并请求完毕，
      退场时内容已经就绪 —— 观感是「揭幕」，不是「加载完再揭幕」。

    时间轴（进入 / 停留 / 退出三段，全部集中在下面的常量与 keyframes）
      0ms    遮罩    深色底立刻铺满 —— 冷启动最忌讳的第一帧白闪由此消除
      60ms   隐白    淡入 + 0.9→1 缩放，640ms 落定
      220ms  金线    横向展开（scaleX 0.1→1），520ms
      360ms  副标题  上浮 10rpx + 淡入，480ms
      560ms  跳过提示 淡入 400ms
      1300ms ——     自动退场（任意点击可提前触发同一条路径）
      +360ms 遮罩    淡出；品牌字同步 1→1.06 轻微扩张，像光透进来
      +400ms ——     通知父级卸载（比 CSS 动画多留 40ms，动画不会被截断）
  -->
  <view class="splash" :class="{ 'splash--leaving': leaving }" @tap="leave">
    <view class="splash-mark">
      <text class="splash-word">隐白</text>
      <view class="splash-rule" />
      <text class="splash-caption">精品咖啡 · 日常之上</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

/* 停留时长：入场级联在 920ms 左右收尾，再留 380ms 静止，读得完但不拖沓 */
const AUTO_HOLD = 1300
/* 卸载时刻：必须 ≥ CSS 里 splash-out 的 360ms，宁多不少 —— 提前卸载会把淡出切掉，露出硬切 */
const EXIT_MS = 400

const emit = defineEmits(['done'])

const leaving = ref(false)
let holdTimer = null
let exitTimer = null

/**
 * 深色开屏期间把状态栏前景改白。
 * 小程序统一用 navigationStyle: custom + 黑色状态栏字，压在 #0b0a07 上等于看不见；
 * 这是 best-effort：个别基础库在自定义导航下不响应，失败只影响这 1.7s 的可读性，
 * 不影响动画本身，所以错误整个吞掉。
 */
function syncStatusBar(dark) {
  // #ifdef MP-WEIXIN
  /* 用 fail 回调兜底而不是 try/catch：这个 API 不支持的场景走的是 fail，不是同步抛错 */
  uni.setNavigationBarColor({
    frontColor: dark ? '#ffffff' : '#000000',
    backgroundColor: dark ? '#0b0a07' : '#fbfaf7',
    fail: () => {
      /* 不支持就保持原色，不打断开屏 */
    }
  })
  // #endif
}

/**
 * 进入退场。
 * 点击与定时器共用这一条路径，所以「跳过」和「自动结束」的观感完全一致；
 * leaving 兼作幂等闸门，连点不会触发二次退场。
 */
function leave() {
  if (leaving.value) return
  leaving.value = true
  clearTimeout(holdTimer)
  exitTimer = setTimeout(() => emit('done'), EXIT_MS)
}

onMounted(() => {
  syncStatusBar(true)
  holdTimer = setTimeout(leave, AUTO_HOLD)
})

onUnmounted(() => {
  clearTimeout(holdTimer)
  clearTimeout(exitTimer)
  /* 遮罩已经淡完、浅色首页已经露出来，这时把状态栏改回黑色才不会有割裂感 */
  syncStatusBar(false)
})

/* 留给父级（如需要被其它交互强制收掉时）主动结束 */
defineExpose({ leave })
</script>

<style lang="scss" scoped>
.splash {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: $z-splash;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* 深色品牌底 + 极淡金色晕光：黑面要有「被灯照到」的层次，否则像一块死黑 */
  background: radial-gradient(60% 44% at 50% 38%, $c-splash-glow, transparent 72%), $c-dark-deep;
  /* 视觉中心略高于几何中心：下方留白多一分，重心才不往下坠 */
  padding-bottom: $sp-8;
}

/* 遮罩只动透明度：一旦给固定层加 transform，它就成了其它 fixed 后代的包含块。
   动画不挂在常态上，只在退场时挂 —— 常态没动画就没有 fill 优先级的问题。 */
.splash--leaving {
  animation: splash-out 360ms $ease-in-out both;
}

.splash-mark {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.splash-word {
  font-family: $font-display;
  font-size: $fs-splash;
  font-weight: 300;
  line-height: 1.05;
  color: $c-on-dark;
  /* 字距拉开到 12rpx，两个字之间要有呼吸；padding-left 抵消末字后多出的字距，否则整体偏左 */
  letter-spacing: 12rpx;
  padding-left: 12rpx;
  animation: splash-word-in 640ms $ease-out 60ms both;
}

.splash-rule {
  width: 56rpx;
  height: 2rpx;
  margin: $sp-5 0 $sp-4;
  background: $c-gold-line;
  animation: splash-rule-in 520ms $ease-out 220ms both;
}

.splash-caption {
  @include caps;
  color: $c-on-dark-faint;
  animation: splash-caption-in 480ms $ease-out 360ms both;
}

.splash-skip {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(#{$sp-8} + env(safe-area-inset-bottom));
  text-align: center;
  color: $c-on-dark-faint;
  font-size: $fs-micro;
  letter-spacing: $ls-wide;
  animation: splash-skip-in 400ms $ease-out 560ms both;
}

/* ---- 入场 ---- */
@keyframes splash-word-in {
  from {
    opacity: 0;
    transform: scale(0.9);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes splash-rule-in {
  from {
    opacity: 0;
    transform: scaleX(0.1);
  }

  to {
    opacity: 1;
    transform: scaleX(1);
  }
}

@keyframes splash-caption-in {
  from {
    opacity: 0;
    transform: translateY(10rpx);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes splash-skip-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

/* ---- 退场 ----
   金线 / 副标题 / 跳过提示不用单独处理：它们是遮罩的子节点，随遮罩一起淡出。
   只有品牌字要继续演（轻微扩张 + 淡出），所以给它换掉入场动画。
   注意必须用 animation 覆盖而不是写 opacity —— 入场动画带 fill: both，
   动画来源的优先级高于普通声明，直接写 opacity 是压不住的。 */
@keyframes splash-out {
  from {
    opacity: 1;
  }

  to {
    opacity: 0;
  }
}

.splash--leaving .splash-word {
  animation: splash-word-out 360ms $ease-in-out both;
}

@keyframes splash-word-out {
  from {
    opacity: 1;
    transform: scale(1);
  }

  to {
    opacity: 0;
    transform: scale(1.06);
  }
}

/* #ifdef H5 */
/* 桌面端与页面列同宽同位置，否则开屏会横跨整个浏览器窗口 */
.splash {
  max-width: $app-max;
  margin: 0 auto;
}

/* 降低动效偏好：去掉缩放与级联，只保留遮罩淡出，元素直接到位 */
@media (prefers-reduced-motion: reduce) {
  .splash-word,
  .splash-rule,
  .splash-caption,
  .splash-skip,
  .splash--leaving .splash-word {
    animation: none;
  }
}
/* #endif */
</style>
