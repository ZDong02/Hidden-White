<template>
  <!--
    生日礼遇弹层：一个弹层承载五种状态，不按状态拆多个组件。
    用户视角只有一件连续的事——「看我的生日礼遇」；状态差异在内容区里消化，
    弹层本身不闪不换，避免点开一个又跳到另一个。

    位置约束：弹层通过 Modal（position: fixed）渲染，必须挂在页面层，
    不能挂进 swiper-item —— 微信端 swiper-item 带 transform，fixed 会相对它定位。
  -->
  <Modal
    :visible="visible"
    :title="status?.title || '生日礼遇'"
    @update:visible="handleVisible"
  >
    <view v-if="!status" class="bd-empty">
      <text class="muted bd-empty-text">{{ error || '正在读取生日礼遇…' }}</text>
    </view>

    <template v-else>
      <!-- 状态一：未填生日 / 主动修改 -->
      <template v-if="editing">
        <text class="bd-desc">填写你的生日，生日当月可领取专属礼遇。只为礼遇使用，不含出生年份。</text>
        <view class="bd-pickers">
          <picker
            class="bd-picker"
            mode="selector"
            :range="monthLabels"
            :value="monthIndex"
            @change="onMonthChange"
          >
            <view class="bd-picker-inner">
              <text class="bd-picker-value">{{ month }} 月</text>
              <view class="bd-picker-caret"><Icon name="chevron-right" tone="faint" :size="22" /></view>
            </view>
          </picker>
          <picker
            class="bd-picker"
            mode="selector"
            :range="dayLabels"
            :value="dayIndex"
            @change="onDayChange"
          >
            <view class="bd-picker-inner">
              <text class="bd-picker-value">{{ day }} 日</text>
              <view class="bd-picker-caret"><Icon name="chevron-right" tone="faint" :size="22" /></view>
            </view>
          </picker>
        </view>
        <text class="bd-hint">生日每年可修改一次，请填写真实日期。</text>
        <text v-if="error" class="bd-error">{{ error }}</text>
      </template>

      <!-- 状态二：可领取 / 已领取 / 非窗口期 -->
      <template v-else>
        <view :class="['bd-gift', { 'bd-gift--done': status.state === 'CLAIMED' }]">
          <view class="bd-gift-top">
            <view class="bd-gift-icon">
              <Icon name="gift" tone="gold-on-dark" :size="40" />
            </view>
            <view class="bd-gift-copy">
              <text class="bd-gift-kicker">{{ giftKicker }}</text>
              <text class="bd-gift-title">{{ giftTitle }}</text>
            </view>
          </view>
          <text class="bd-gift-desc">{{ giftDesc }}</text>
        </view>

        <view class="bd-meta">
          <view class="bd-meta-row">
            <text class="bd-meta-label">生日</text>
            <text class="bd-meta-value">{{ status.birthdayMonth }} 月 {{ status.birthdayDay }} 日</text>
          </view>
          <view class="bd-meta-row">
            <text class="bd-meta-label">礼遇窗口</text>
            <text class="bd-meta-value">{{ windowText }}</text>
          </view>
          <view v-if="status.state === 'CLAIMED' && status.claim" class="bd-meta-row">
            <text class="bd-meta-label">领取时间</text>
            <text class="bd-meta-value">{{ status.claim.claimedAt }}</text>
          </view>
        </view>

        <text v-if="editable" class="bd-link" hover-class="bd-link--press" :hover-stay-time="60" @tap="startEdit">
          修改生日
        </text>
      </template>

      <text v-if="!editing && error" class="bd-error">{{ error }}</text>
    </template>

    <template #footer>
      <UiButton v-if="editing" :disabled="submitting" @tap="submitBirthday">
        {{ submitting ? '保存中…' : '保存生日' }}
      </UiButton>
      <UiButton v-else-if="status?.state === 'CLAIMABLE'" :disabled="submitting" @tap="submitClaim">
        {{ submitting ? '领取中…' : `立即领取 ${status.pointsAmount} 积分` }}
      </UiButton>
      <UiButton v-else variant="outline" @tap="close">
        {{ status?.state === 'CLAIMED' ? '完成' : '知道了' }}
      </UiButton>
    </template>
  </Modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Modal from './ui/Modal.vue'
import Icon from './ui/Icon.vue'
import UiButton from './ui/Button.vue'
import { showError, showSuccess } from '../utils/toast'
import { birthday, claimBirthday, saveBirthday } from '../store/birthday'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'claimed'])

const status = computed(() => birthday.data)
const error = ref('')
const submitting = ref(false)
const editing = ref(false)
const month = ref(1)
const day = ref(1)

const monthLabels = Array.from({ length: 12 }, (_, index) => `${index + 1} 月`)
// 2 月给 29 天：闰年出生的人不该在填写环节就被挡掉，平年由服务端归一
const dayLabels = computed(() => {
  const total = daysInMonth(month.value)
  return Array.from({ length: total }, (_, index) => `${index + 1} 日`)
})
const monthIndex = computed(() => month.value - 1)
const dayIndex = computed(() => day.value - 1)

const editable = computed(() => Boolean(status.value?.editable))

/** 与点单端一致的闰年上界：2000 是闰年，2 月按 29 天算。 */
function daysInMonth(value) {
  if (value === 2) return 29
  return [4, 6, 9, 11].includes(value) ? 30 : 31
}

const giftKicker = computed(() => {
  if (status.value?.state === 'CLAIMED') return 'BIRTHDAY GIFT · 已领取'
  return `BIRTHDAY GIFT · ${status.value?.claim?.year ?? new Date().getFullYear()}`
})

const giftTitle = computed(() => {
  const value = status.value
  if (!value) return ''
  if (value.state === 'CLAIMED') return `${value.claim?.amount ?? value.pointsAmount} 积分已入账`
  return `${value.pointsAmount} 积分`
})

const giftDesc = computed(() => {
  const value = status.value
  if (!value) return ''
  if (value.state === 'CLAIMED') return '积分已计入账户，可在积分明细中查看。愿这一岁如常所愿。'
  if (value.state === 'CLAIMABLE') return value.description || '生日当月可领取一次，积分即时到账。'
  if (value.state === 'NOT_IN_WINDOW') {
    const days = value.daysUntil
    return days > 0 ? `距离你的生日还有 ${value.daysUntil} 天，到时记得来领。` : '生日月开放领取。'
  }
  return '生日礼遇正在筹备中，敬请期待。'
})

const windowText = computed(() => {
  const value = status.value
  if (!value?.windowFrom || !value?.windowTo) return '—'
  return `${value.windowFrom} ~ ${value.windowTo}`
})

/** 每次打开都把本地选择同步成已保存的生日，避免上次未提交的选择残留。 */
watch(
  () => props.visible,
  (open) => {
    if (!open) return
    error.value = ''
    submitting.value = false
    const current = status.value
    month.value = current?.birthdayMonth || 1
    day.value = current?.birthdayDay || 1
    editing.value = !current || current.state === 'NO_BIRTHDAY'
  }
)

function onMonthChange(event) {
  month.value = Number(event.detail.value) + 1
  // 1 月 31 日切到 2 月：不能让 31 日悬空
  if (day.value > daysInMonth(month.value)) day.value = daysInMonth(month.value)
}

function onDayChange(event) {
  day.value = Number(event.detail.value) + 1
}

function startEdit() {
  error.value = ''
  month.value = status.value?.birthdayMonth || 1
  day.value = status.value?.birthdayDay || 1
  editing.value = true
}

async function submitBirthday() {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await saveBirthday(month.value, day.value)
    editing.value = false
    showSuccess('生日已保存')
  } catch (exception) {
    error.value = exception.message || '保存失败，请稍后再试'
    showError(error.value)
  } finally {
    submitting.value = false
  }
}

async function submitClaim() {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const result = await claimBirthday()
    if (result?.state === 'CLAIMED') {
      showSuccess(`已领取 ${result.claim?.amount ?? result.pointsAmount} 积分`)
      emit('claimed', result)
    }
  } catch (exception) {
    error.value = exception.message || '领取失败，请稍后再试'
    showError(error.value)
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:visible', false)
}

function handleVisible(next) {
  if (!next) close()
}
</script>

<style lang="scss" scoped>
.bd-empty {
  padding: $sp-6 0;
  text-align: center;
}

.bd-empty-text {
  font-size: $fs-body-sm;
}

.bd-desc {
  display: block;
  color: $c-ink-muted;
  font-size: $fs-body-sm;
  line-height: 1.6;
}

/* ---- 生日选择：两个等宽的月/日选择器，比原生日期控件更贴近「只收月日」 ---- */
.bd-pickers {
  display: flex;
  gap: $sp-3;
  margin-top: $sp-5;
}

.bd-picker {
  flex: 1;
  min-width: 0;
}

.bd-picker-inner {
  display: flex;
  min-height: $tap-min;
  align-items: center;
  justify-content: space-between;
  gap: $sp-2;
  padding: 0 $sp-4;
  border: 1rpx solid $c-border-strong;
  border-radius: $r-md;
  background: $c-surface-2;
}

/* 图标集里只有右向箭头，旋转 90° 充当向下箭头：不为一个方向再产一套 PNG */
.bd-picker-caret {
  display: flex;
  transform: rotate(90deg);
}

.bd-picker-value {
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: $fw-semibold;
}

.bd-hint {
  display: block;
  margin-top: $sp-3;
  color: $c-ink-faint;
  font-size: $fs-caption;
}

.bd-error {
  display: block;
  margin-top: $sp-3;
  color: $c-danger;
  font-size: $fs-caption;
  line-height: 1.5;
}

/* ---- 礼遇卡：深色面承载仪式感，与会员卡同一套材质 ---- */
.bd-gift {
  padding: $sp-5;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  background: linear-gradient(135deg, $c-dark-soft 0%, $c-dark 58%, $c-dark-deep 100%);
  box-shadow: $sh-dark;
}

.bd-gift--done {
  border-color: $c-hairline-gold;
}

.bd-gift-top {
  display: flex;
  align-items: center;
  gap: $sp-3;
}

.bd-gift-icon {
  display: flex;
  width: 72rpx;
  height: 72rpx;
  flex: 0 0 72rpx;
  align-items: center;
  justify-content: center;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-sm;
  background: rgba(212, 173, 47, 0.12);
}

.bd-gift-copy {
  min-width: 0;
}

.bd-gift-kicker {
  display: block;
  @include caps;
  color: $c-gold-on-dark;
  opacity: 0.85;
}

.bd-gift-title {
  display: block;
  margin-top: $sp-0;
  color: $c-on-dark;
  font-family: $font-display;
  font-size: $fs-h2;
  letter-spacing: $ls-normal;
  line-height: $lh-tight;
}

.bd-gift-desc {
  display: block;
  margin-top: $sp-4;
  color: $c-on-dark-muted;
  font-size: $fs-caption;
  line-height: 1.6;
}

/* ---- 信息行：生日 / 窗口 / 领取时间 ---- */
.bd-meta {
  margin-top: $sp-4;
  padding: $sp-3 $sp-4;
  border-radius: $r-md;
  background: $c-surface-2;
}

.bd-meta-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  padding: $sp-0 0;
}

.bd-meta-label {
  flex-shrink: 0;
  color: $c-ink-subtle;
  font-size: $fs-caption;
}

.bd-meta-value {
  color: $c-ink;
  font-size: $fs-caption;
  text-align: right;
}

.bd-link {
  display: inline-block;
  margin-top: $sp-4;
  color: $c-gold-text;
  font-size: $fs-caption;
  text-decoration: underline;
}

.bd-link--press {
  opacity: 0.6;
}
</style>
