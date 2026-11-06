<template>
  <!--
    免费升杯说明弹层：一个弹层承载四种状态（可免费 / 仅积分 / 用完 / 不可用）。
    用户视角只有一件连续的事——「我还剩几次、怎么用」，状态差异在文案里消化。
  -->
  <Modal :visible="visible" title="免费升杯" @update:visible="handleVisible">
    <view v-if="!data" class="up-empty">
      <text class="muted up-empty-text">{{ error || '正在读取升杯权益…' }}</text>
    </view>

    <template v-else>
      <view class="up-hero">
        <view class="up-badge">
          <Icon name="cup" tone="gold" :size="34" />
        </view>
        <text class="up-headline">{{ headline }}</text>
        <text class="up-subline">{{ subline }}</text>
      </view>

      <view class="up-rules">
        <view v-for="(rule, index) in rules" :key="index" class="up-rule">
          <view class="up-dot" />
          <text class="up-rule-text">{{ rule }}</text>
        </view>
      </view>

      <view class="up-foot">
        <Button variant="primary" @tap="goOrder">去点单</Button>
      </view>
    </template>
  </Modal>
</template>

<script setup>
import { computed } from 'vue'
import Modal from './ui/Modal.vue'
import Button from './ui/Button.vue'
import Icon from './ui/Icon.vue'
import { upsize, loadUpsize } from '../store/upsize'
import { openPage } from '../utils/navigation'

defineProps({
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible'])

const data = computed(() => upsize.data)
const error = computed(() => upsize.error)

/**
 * 主文案：一句话回答「我现在能怎么升」。
 *
 * <p>免费次数用 {@code freeLeft === -1} 判断「不限」而不是 {@code > 0}——
 * 服务端用 -1 表达不限次（钻石会员），当成 0 会显示成「本月已用完」。
 */
const headline = computed(() => {
  const value = data.value
  if (!value) return ''
  if (value.freeLeft === -1) return '本月免费升杯不限次'
  if (value.freeLeft > 0) return `本月还可免费升 ${value.freeLeft} 次`
  if (value.available) return `每次 ${value.pointsCost} 积分`
  switch (value.reason) {
    case 'INSUFFICIENT_POINTS':
      return '积分不足'
    case 'LIMIT_REACHED':
      return '本月已用完'
    case 'NO_POINTS_COST':
      return '本月免费次数已用完'
    default:
      return '暂不可用'
  }
})

const subline = computed(() => {
  const value = data.value
  if (!value) return ''
  if (value.note) return value.note
  return value.available ? '结算页勾选即可使用' : '升级等级可解锁更多升杯次数'
})

/**
 * 规则说明由服务端数据拼装，不写死文案。
 *
 * <p>运营改额度或积分价后，这里的句子必须跟着变 ——
 * 写死的话就会出现「文案说 100 积分、实际扣 150」。
 */
const rules = computed(() => {
  const value = data.value
  if (!value) return []
  const list = []
  if (value.fromSize && value.toSize) {
    list.push(`${value.fromSize} 免费升级为 ${value.toSize}`)
  }
  if (value.pointsCost > 0) {
    list.push(`免费次数用完后，每次消耗 ${value.pointsCost} 积分`)
  } else if (value.freeLeft === -1) {
    list.push('本等级免费升杯不限次数')
  }
  list.push('每单限升一杯，额度每月 1 日 0 点重置')
  list.push('积分升杯与会员折扣、优惠券同时使用时，升杯不参与打折、不抵扣门槛')
  return list
})

function handleVisible(next) {
  emit('update:visible', next)
}

function goOrder() {
  emit('update:visible', false)
  openPage('index')
}

defineExpose({
  refresh: () => loadUpsize(true)
})
</script>

<style lang="scss" scoped>
.up-empty {
  padding: $sp-6 0;
  text-align: center;
}

.up-empty-text {
  font-size: $fs-body-sm;
}

.up-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: $sp-5;
}

.up-badge {
  display: flex;
  width: 72rpx;
  height: 72rpx;
  align-items: center;
  justify-content: center;
  border-radius: $r-md;
  background: $c-gold-soft;
}

.up-headline {
  margin-top: $sp-4;
  color: $c-ink-strong;
  font-family: $font-display;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.up-subline {
  margin-top: $sp-2;
  color: $c-ink-muted;
  font-size: $fs-body-sm;
  line-height: 1.6;
  text-align: center;
}

.up-rules {
  display: flex;
  flex-direction: column;
  gap: $sp-3;
  padding: $sp-4;
  border-radius: $r-md;
  background: $c-bg-sunken;
}

.up-rule {
  display: flex;
  align-items: flex-start;
  gap: $sp-2;
}

.up-dot {
  width: 8rpx;
  height: 8rpx;
  flex-shrink: 0;
  margin-top: 12rpx;
  border-radius: $r-pill;
  background: $c-gold;
}

.up-rule-text {
  flex: 1;
  color: $c-ink-muted;
  font-size: $fs-caption;
  line-height: 1.65;
}

.up-foot {
  margin-top: $sp-5;
}
</style>
