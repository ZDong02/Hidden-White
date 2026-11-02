<template>
  <!--
    首页推荐轮播。
    数据来自管理端「咖啡运营 · 推荐商品」，端上从推荐池随机抽最多 3 款（见 store/index.js）。
    池子为空时整块不渲染：留一张空的黑卡比没有更糟。

    交互与性能要点（改动前先读）：
      · 切换动画全部由原生 swiper 承担，JS 不在动画帧里做任何事；
      · 图片按屏懒加载：首帧只挂当前屏的 src，相邻屏空闲预热，用户一触摸就全部放行；
      · 自动播放在「面板不可见 / 手指按住 / 系统偏好减少动效」三种情况下一律暂停；
      · 指示点纯展示且不拦截手势（pointer-events: none），避免抢走卡片的点击与滑动。

    间距要点（v3 修复「相邻卡片粘连」）：
      swiper-item 的宽度恒等于 swiper 宽度，卡片若直接铺满 item，两张卡就是零间隙的
      两块深色面 —— 圆角、描边、阴影会互相压住，滑动过程中看起来像一张被切开的卡。
      做法：item 内套一层 .hero-slot 承担内边距，swiper 用等量负边距抵消，
      于是「首屏仍与页面栅格对齐」+「相邻屏之间始终有一条 24rpx 的缝」。
  -->
  <view v-if="slides.length" class="hero">
    <swiper
      class="hero-swiper"
      :current="current"
      :autoplay="autoplayEnabled"
      :interval="interval"
      :duration="duration"
      easing-function="easeOutCubic"
      :circular="circular"
      @change="onChange"
      @touchstart="onTouchStart"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <swiper-item v-for="(slide, index) in slides" :key="slide.id">
        <view class="hero-slot">
          <RecommendSlide
            :slide="slide"
            :eager="eagerList.includes(index)"
            @select="emit('select', $event)"
          />
        </view>
      </swiper-item>
    </swiper>

    <!--
      自定义指示点而不是 swiper 的 indicator-dots：
      原生点是白/灰方块，无法与金色体系对齐。
      v3 起移到卡片外：卡片里操作行贴着底边，点叠在里面必然压住「价格」那一行；
      放到卡下自成一行 10rpx，只多吃 20rpx，换来干净的边界。
    -->
    <view v-if="slides.length > 1" class="hero-dots" aria-hidden="true">
      <view
        v-for="(slide, index) in slides"
        :key="`dot-${slide.id}`"
        :class="['hero-dot', { 'hero-dot--active': index === activeIndex }]"
      />
    </view>
  </view>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import RecommendSlide from './RecommendSlide.vue'
import { mergeIndexes, neighborIndexes } from '../utils/recommend'

const props = defineProps({
  /** 当前要轮播的推荐（最多 3 项，由 store 抽样） */
  slides: {
    type: Array,
    default: () => []
  },
  /** 所在面板不可见（切到别的 Tab）时为真：此时停掉自动播放，别做无人观看的轮播 */
  paused: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select'])

/** 自动轮播间隔：够读完标题与短句，又不至于让人等下一屏 */
const interval = 5600
/** 切换时长：320ms 比 340ms 更跟手，仍足以看清滑动方向（距 tokens 的 base 档 300ms 最近） */
const duration = 320
/** 首帧之后多久开始预热相邻屏：留出首屏渲染与主接口的带宽 */
const WARMUP_DELAY = 700
/** 手指离开后多久恢复自动播放：等切换惯性动画落定，避免刚滑完就被自动切走 */
const RESUME_DELAY = 320

/** 受控下标：只在「轮播池换人 / 用户点指示点」时写，动画过程中的每一次 change 也回写一次以保持同步 */
const current = ref(0)
/** 指示点高亮位置 */
const activeIndex = ref(0)
/** 手指是否按在轮播上 */
const touching = ref(false)
/** 已放行加载的下标集合（数组形态，元素极少，includes 足够快） */
const eagerList = ref([0])
/** H5 下系统「减少动效」偏好：命中后不自动轮播 */
const reducedMotion = ref(false)

const circular = computed(() => props.slides.length > 1)
const autoplayEnabled = computed(
  () => props.slides.length > 1 && !props.paused && !touching.value && !reducedMotion.value
)
/** 池子内容指纹：换人时重置到第一屏 */
const slideKey = computed(() => props.slides.map((slide) => slide.id).join(','))

let warmupTimer = null
let resumeTimer = null
let motionQuery = null
let motionHandler = null

function scheduleWarmup() {
  clearTimeout(warmupTimer)
  warmupTimer = setTimeout(() => {
    eagerList.value = mergeIndexes(eagerList.value, neighborIndexes(activeIndex.value, props.slides.length))
  }, WARMUP_DELAY)
}

/** 放行某一屏及其相邻屏的图片加载 */
function markEager(index) {
  eagerList.value = mergeIndexes(eagerList.value, neighborIndexes(index, props.slides.length))
}

function onChange(event) {
  const index = event.detail.current
  activeIndex.value = index
  // 回写受控下标：否则点指示点时会拿一个过期的值去比较，出现「点了没反应」
  current.value = index
  markEager(index)
}

function onTouchStart() {
  touching.value = true
  // 手指已经按上来了：全部放行，滑过去的那一刻就有图，不等 change 回来再加载
  eagerList.value = props.slides.map((_, index) => index)
}

function onTouchEnd() {
  clearTimeout(resumeTimer)
  resumeTimer = setTimeout(() => {
    touching.value = false
  }, RESUME_DELAY)
}

// 池子换人（管理端改配置 / 重新抽样）：回到第一屏，图片加载状态跟着重置
watch(slideKey, () => {
  current.value = 0
  activeIndex.value = 0
  eagerList.value = [0]
  scheduleWarmup()
})

onMounted(() => {
  eagerList.value = [0]
  scheduleWarmup()

  // H5：尊重系统的「减少动效」设置，命中后不自动轮播（仍可手动滑）
  // #ifdef H5
  motionQuery = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null
  if (motionQuery) {
    reducedMotion.value = motionQuery.matches
    motionHandler = (event) => {
      reducedMotion.value = event.matches
    }
    motionQuery.addEventListener?.('change', motionHandler)
  }
  // #endif
})

onBeforeUnmount(() => {
  clearTimeout(warmupTimer)
  clearTimeout(resumeTimer)
  motionQuery?.removeEventListener?.('change', motionHandler)
})
</script>

<style lang="scss" scoped>
/* 卡片高度必须与 RecommendSlide 的 $hero-card-h 保持一致（328rpx），
   两侧各留 $sp-2 作为阴影呼吸位，故 swiper 高 = 328 + 12 × 2。 */
$hero-card-h: 328rpx;
$hero-gap-half: $sp-2; // 卡片间距 24rpx 的一半，由 slot 内边距实现

.hero {
  position: relative;
}

/* 负边距抵消 slot 内边距：当前屏的卡片左右边缘仍与页面栅格对齐，
   而相邻屏之间留出 2 × 12rpx 的缝；上下各 12rpx 让卡片阴影不被 swiper 裁掉。 */
.hero-swiper {
  height: $hero-card-h + $hero-gap-half * 2;
  margin: (-$hero-gap-half) (-$hero-gap-half) 0;
}

/* 每一屏的容器：只负责留白，不参与任何视觉 */
.hero-slot {
  box-sizing: border-box;
  height: 100%;
  padding: $hero-gap-half;
}

/* ---- 指示点：卡片下方独立一行（卡内会被「价格」那一行压住） ---- */
.hero-dots {
  display: flex;
  height: 10rpx;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  margin-top: $sp-2;
  /* 纯展示：不拦截手势，点击与滑动都交给卡片本身 */
  pointer-events: none;
}

.hero-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: $r-pill;
  background: rgba(185, 151, 55, 0.42);
  transition: width $dur-base $ease-spring, background-color $dur-fast ease;
}

/* 当前屏拉成一小段金线：比「换个颜色」更容易在余光里分辨位置 */
.hero-dot--active {
  width: 28rpx;
  background: $c-gold;
}
</style>
