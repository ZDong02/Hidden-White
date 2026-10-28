<template>
  <view class="screen log-screen">
    <PhoneHeader title="积分明细" />

    <view class="log-body">
      <!-- 当前积分：与会员卡同一档深色表面，避免两个页面之间视觉断裂 -->
      <view class="summary-card card-dark reveal">
        <view class="summary-copy">
          <text class="summary-label">当前积分</text>
          <text class="summary-value num">{{ points }}</text>
          <text class="summary-hint">每笔变动均有记录，可追溯到对应订单或兑换单</text>
        </view>
        <view class="summary-mark">
          <Icon name="receipt" tone="gold-on-dark" :size="88" />
        </view>
      </view>

      <view v-if="!loggedIn" class="empty">
        <Icon name="user" tone="muted" :size="56" />
        <text class="muted empty-text">登录后即可查看积分明细</text>
        <UiButton variant="primary" class="empty-action" @tap="goLogin">去登录</UiButton>
      </view>

      <template v-else>
        <view class="section-head">
          <text class="section-title head-title">全部记录</text>
          <text class="head-note">{{ total ? `${total} 笔` : '按时间倒序' }}</text>
        </view>

        <view v-if="loading && !rows.length" class="skeleton-list">
          <view class="skeleton skeleton-row" />
          <view class="skeleton skeleton-row" />
          <view class="skeleton skeleton-row" />
        </view>

        <view v-else-if="!rows.length" class="empty">
          <Icon name="receipt" tone="muted" :size="56" />
          <text class="muted empty-text">还没有积分记录，完成一笔订单后就会出现在这里</text>
        </view>

        <view v-for="row in rows" :key="row.id" class="log-row">
          <view class="log-main">
            <text class="log-title">{{ labelOf(row) }}</text>
            <text class="log-time">{{ formatDateTime(row.createdAt).datetime }}</text>
          </view>
          <view class="log-side">
            <text :class="['log-amount', 'num', row.changeAmount > 0 ? 'log-amount--in' : 'log-amount--out']">
              {{ signed(row.changeAmount) }}
            </text>
            <text class="muted log-balance num">余额 {{ row.balanceAfter }}</text>
          </view>
        </view>

        <view v-if="rows.length && rows.length < total" class="load-more">
          <text class="muted">{{ loadingMore ? '正在加载…' : '上滑加载更多' }}</text>
        </view>

        <view class="footnote">积分规则：消费 1 元 = 1 积分；100 积分 ≈ 3 元等值兑换</view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import Icon from '../../components/ui/Icon.vue'
import UiButton from '../../components/ui/Button.vue'
import { requestApi } from '../../config/backend'
import { openPage } from '../../utils/navigation'
import { currentUser, isLoggedIn } from '../../store/auth'
import { showError } from '../../utils/toast'
import { formatDateTime } from '../../utils/datetime'

const PAGE_SIZE = 15

/**
 * 流水业务类型 → 中文文案。
 * 与后端 PointsBizType 枚举同名；新增类型时这里必须同步，否则会显示成英文枚举名。
 */
const BIZ_LABEL = {
  ORDER_AWARD: '消费获得',
  REDEEM: '积分兑换',
  REDEEM_CANCEL: '取消兑换退回',
  REDEEM_EXPIRE_REFUND: '逾期未核销退回',
  POINTS_DEDUCT: '积分抵现',
  POINTS_REFUND: '取消订单退回',
  ADMIN_ADJUST: '管理调整'
}

const loggedIn = computed(() => isLoggedIn.value)
const points = ref(0)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
let page = 1
let started = false

async function loadPoints() {
  if (!currentUser.value?.id) return
  try {
    const member = await requestApi(`/members/${encodeURIComponent(currentUser.value.id)}`)
    points.value = Number(member?.points) || 0
  } catch (error) {
    // 余额读不到不该阻断流水列表：流水本身带着 balanceAfter
    console.warn('积分余额读取失败', error)
  }
}

async function loadLogs(reset = false) {
  if (reset) {
    page = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const data = await requestApi(`/members/points-log?pageNum=${page}&pageSize=${PAGE_SIZE}`)
    const list = Array.isArray(data?.rows) ? data.rows : []
    rows.value = reset ? list : [...rows.value, ...list]
    total.value = Number(data?.total) || 0
  } catch (error) {
    showError(error.message || '积分明细加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function refresh() {
  started = true
  loadPoints()
  loadLogs(true)
}

onLoad(() => {
  if (!isLoggedIn.value) return
  refresh()
})

onShow(() => {
  // 未登录进页 → 登录返回：补一次加载；已在页内刷新过的不会重复请求
  if (isLoggedIn.value && !started) refresh()
})

onReachBottom(() => {
  if (loadingMore.value || loading.value) return
  if (rows.value.length >= total.value) return
  page += 1
  loadLogs(false)
})

function labelOf(row) {
  return BIZ_LABEL[row.bizType] || row.bizType
}

/** 收入带 +，支出带 −（U+2212，与全站金额符号一致），不只是靠颜色区分 */
function signed(amount) {
  const value = Number(amount) || 0
  return value > 0 ? `+${value}` : `−${Math.abs(value)}`
}

function goLogin() {
  openPage('auth', { redirect: 'pointsLog' })
}
</script>

<style lang="scss" scoped>
.log-screen {
  min-height: 100vh;
}

.log-body {
  padding: $sp-6 $gutter $sp-8;
}

/* ---- 余额概览 ---- */
.summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 200rpx;
  padding: $sp-6;
}

.summary-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  max-width: calc(100% - 110rpx);
}

.summary-label {
  display: block;
  @include caps;
  color: $c-on-dark-muted;
}

.summary-value {
  display: block;
  margin-top: $sp-1;
  color: $c-gold-on-dark;
  font-size: $fs-display;
  font-weight: 600;
  line-height: $lh-tight;
}

.summary-hint {
  display: block;
  margin-top: $sp-2;
  color: $c-on-dark-muted;
  font-size: $fs-micro;
  line-height: $lh-body;
}

.summary-mark {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  opacity: 0.28;
  pointer-events: none;
}

/* ---- 区块标题 ---- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-6 2rpx $sp-3;
}

.head-title {
  margin-bottom: 0;
}

.head-note {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---- 流水行：一行一张卡，与兑换记录、历史订单同一套卡片语汇 ---- */
.log-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  min-height: 132rpx;
  padding: $sp-3 $sp-5;
  margin-bottom: $sp-2;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
}

.log-main {
  min-width: 0;
  flex: 1;
}

.log-title {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: 600;
  @include ellipsis;
}

.log-time {
  display: block;
  margin-top: $sp-0;
  color: $c-ink-muted;
  font-size: $fs-micro;
}

.log-side {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $sp-0;
}

.log-amount {
  font-size: $fs-title-sm;
  font-weight: 600;
}

/* 收入用深金（浅底上唯一够对比度的强调色），支出回到正文墨色：
   支出不是「错误」，用红会误导；金额的正负号本身已经表达方向。 */
.log-amount--in {
  color: $c-gold-text;
}

.log-amount--out {
  color: $c-ink;
}

.log-balance {
  font-size: $fs-micro;
}

.load-more {
  padding: $sp-4 0 $sp-1;
  text-align: center;
  font-size: $fs-micro;
}

/* ---- 空态 / 骨架 ---- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  padding: $sp-7 $sp-4;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-lg;
  background: $c-surface-2;
}

.empty-text {
  font-size: $fs-caption;
  text-align: center;
}

.empty-action {
  width: 100%;
  margin-top: $sp-2;
}

.skeleton-list {
  display: block;
}

.skeleton-row {
  height: 132rpx;
  margin-bottom: $sp-2;
  border-radius: $r-lg;
}

.footnote {
  padding: $sp-5 0 $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
  line-height: $lh-body;
}

@media (max-width: 340px) {
  .log-body {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .log-row {
    padding: $sp-3 $sp-4;
  }
}
</style>
