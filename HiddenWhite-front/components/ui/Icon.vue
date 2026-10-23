<template>
  <!--
    统一图标出口。

    为什么不是 <text> 里的字形符号（咖啡杯、星标、箭头这类 Unicode 字符）：
      字形随系统字体走，同一个字符在 iOS / 安卓 / 微信 H5 上是三种粗细与比例，
      既不能统一视觉重量，也没法统一颜色。项目里所有图标一律走这里。

    为什么是 <image> 而不是 SVG：
      微信小程序 <image> 不支持 currentColor 继承，CSS filter 在部分安卓 webview 无效。
      因此按色版预生成 PNG（见 scripts/generate-icons.cjs），由 tone 选图。
      新增图标：往 scripts/icons 放母版 + 在 config/icon-set.json 登记色版 + npm run assets:icons。
  -->
  <image
    class="ui-icon"
    :src="src"
    :style="sizeStyle"
    mode="aspectFit"
    :aria-hidden="decorative ? 'true' : 'false'"
  />
</template>

<script setup>
import { computed } from 'vue'
import iconSet from '../../config/icon-set.json'

const props = defineProps({
  /** 图标名，必须已登记在 config/icon-set.json */
  name: {
    type: String,
    required: true
  },
  /**
   * 色版：
   *   ink            浅底上的主图标
   *   muted          浅底上的次级图标
   *   faint          浅底上的弱化图标（箭头等非信息图形）
   *   gold           浅底上的金色图标
   *   gold-on-dark   深底上的金色图标
   *   decor          装饰性金（不承载信息，如空态图形）
   */
  tone: {
    type: String,
    default: 'ink'
  },
  /** 尺寸，单位 rpx；只允许取 $icon-sm / $icon-md / $icon-lg / $icon-xl 附近的值 */
  size: {
    type: Number,
    default: 40
  },
  /** 纯装饰图形，读屏应跳过 */
  decorative: {
    type: Boolean,
    default: true
  }
})

// 未登记的图标名返回空串（不渲染任何图形），不让页面崩在图片 404 上
const src = computed(() => {
  const tones = iconSet.icons[props.name]
  if (!tones) return ''
  // 该图标没出这个色版时退回它声明的第一个色版，保证永远有图可显示
  const tone = tones.includes(props.tone) ? props.tone : tones[0]
  return `/static/icons/${props.name}-${tone}.png`
})

const sizeStyle = computed(() => `width: ${props.size}rpx; height: ${props.size}rpx;`)
</script>

<style lang="scss" scoped>
.ui-icon {
  display: block;
  flex-shrink: 0;
}
</style>
