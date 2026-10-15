/**
 * 优化使用示例
 *
 * 本文件展示如何使用新创建的可复用组件和工具函数来优化现有代码
 */

/* ============================================================================
 * 示例 1: 使用统一的 UI 组件
 * ============================================================================ */

// ---- 旧代码（auth.vue）----
/*
<view class="field">
  <text class="field-label">手机号</text>
  <input v-model="phone" class="field-input" type="text" maxlength="20" placeholder="请输入手机号" />
</view>
<button class="black-button submit-button" :disabled="submitting" @tap="submit">
  {{ submitting ? '请稍候…' : '登录' }}
</button>
*/

// ---- 新代码（使用 UI 组件）----
/*
<Input
  v-model="phone"
  label="手机号"
  type="text"
  :maxlength="20"
  placeholder="请输入手机号"
  :error="phoneError"
/>
<Button variant="primary" :disabled="submitting" @tap="submit">
  {{ submitting ? '请稍候…' : '登录' }}
</Button>
*/

/* ============================================================================
 * 示例 2: 使用步进器组件
 * ============================================================================ */

// ---- 旧代码（index.vue 购物袋）----
/*
<view class="stepper">
  <button @tap="changeQuantity(index, -1)">−</button>
  <text>{{ item.quantity }}</text>
  <button @tap="changeQuantity(index, 1)">＋</button>
</view>
*/

// ---- 新代码（使用 Stepper 组件）----
/*
<Stepper
  :value="item.quantity"
  :min="1"
  :max="99"
  @change="(newValue) => updateQuantity(index, newValue)"
/>
*/

/* ============================================================================
 * 示例 3: 使用提示工具函数
 * ============================================================================ */

// ---- 旧代码 ----
/*
uni.showToast({ title: '登录成功', icon: 'success' })
uni.showToast({ title: error.message || '操作失败', icon: 'none' })
uni.showModal({
  title: '确认下单',
  content: '当前尚未接入微信支付...',
  success: (res) => {
    if (res.confirm) {
      // 处理确认
    }
  }
})
*/

// ---- 新代码（使用 toast 工具）----
/*
import { showSuccess, showError, showConfirm } from '@/utils/toast'

showSuccess('登录成功')
showError(error.message || '操作失败')

const confirmed = await showConfirm({
  title: '确认下单',
  content: '当前尚未接入微信支付...',
  confirmText: '确认',
  cancelText: '取消'
})
if (confirmed) {
  // 处理确认
}
*/

/* ============================================================================
 * 示例 4: 使用存储工具
 * ============================================================================ */

// ---- 旧代码 ----
/*
function saveCart() {
  try {
    uni.setStorageSync('hidden-white-cart', JSON.parse(JSON.stringify(shop.cart)))
  } catch (error) {
    console.error('保存失败:', error)
  }
}
*/

// ---- 新代码（使用 storage 工具）----
/*
import { setStorageDebounced, setStorageImmediate } from '@/utils/storage'

// 连续修改时防抖存储（如购物车加减）
function saveCart() {
  setStorageDebounced('hidden-white-cart', shop.cart)
}

// 关键操作立即存储（如下单、登录）
function saveCartNow() {
  setStorageImmediate('hidden-white-cart', shop.cart)
}
*/

/* ============================================================================
 * 示例 5: 使用验证工具
 * ============================================================================ */

// ---- 旧代码 ----
/*
async function submit() {
  const mobile = phone.value.trim()
  if (!mobile || !password.value) {
    uni.showToast({ title: '请输入手机号和密码', icon: 'none' })
    return
  }
  // ... 提交逻辑
}
*/

// ---- 新代码（使用 validators）----
/*
import { validatePhone, validatePassword } from '@/utils/validators'
import { showError } from '@/utils/toast'

async function submit() {
  const phoneResult = validatePhone(phone.value)
  if (!phoneResult.valid) {
    showError(phoneResult.message)
    return
  }

  const passwordResult = validatePassword(password.value)
  if (!passwordResult.valid) {
    showError(passwordResult.message)
    return
  }

  // 使用验证后的值
  await loginAccount({
    phone: phoneResult.value,
    password: passwordResult.value
  })
}
*/

/* ============================================================================
 * 示例 6: 使用组合式函数管理状态
 * ============================================================================ */

// ---- 旧代码 ----
/*
const submitting = ref(false)

async function submit() {
  if (submitting.value) return
  try {
    submitting.value = true
    await loginAccount({ phone: phone.value, password: password.value })
    uni.showToast({ title: '登录成功', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: error.message || '操作失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
*/

// ---- 新代码（使用 useLoading）----
/*
import { useLoading } from '@/utils/composables'
import { showSuccess, showError } from '@/utils/toast'

const { loading: submitting, withLoading } = useLoading()

async function submit() {
  try {
    await withLoading(async () => {
      await loginAccount({ phone: phone.value, password: password.value })
    })
    showSuccess('登录成功')
  } catch (error) {
    showError(error.message || '操作失败')
  }
}
*/

/* ============================================================================
 * 示例 7: 使用价格计算工具
 * ============================================================================ */

// ---- 旧代码 ----
/*
export const count = computed(() => {
  return shop.cart.reduce((sum, item) => sum + item.quantity, 0)
})

export const subtotal = computed(() => {
  return shop.cart.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
})

export const total = computed(() => {
  return subtotal.value - discount.value + BUSINESS.packagingFee
})
*/

// ---- 新代码（使用 price 工具）----
/*
import { calculateCartCount, calculateCartTotal, calculateFinalPrice } from '@/utils/price'

export const count = computed(() => calculateCartCount(shop.cart))
export const subtotal = computed(() => calculateCartTotal(shop.cart))
export const total = computed(() =>
  calculateFinalPrice(subtotal.value, discount.value, BUSINESS.packagingFee)
)
*/

/* ============================================================================
 * 示例 8: 使用日期时间工具
 * ============================================================================ */

// ---- 旧代码 ----
/*
const pad = (n) => String(n).padStart(2, '0')
const d = new Date()
const formatted = `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`
*/

// ---- 新代码（使用 datetime 工具）----
/*
import { formatDateTime, formatRelativeTime, isToday } from '@/utils/datetime'

const formatted = formatDateTime(new Date()).datetime
const relative = formatRelativeTime(order.createdAt) // "5分钟前"
const today = isToday(order.createdAt) // true/false
*/

/* ============================================================================
 * 示例 9: 使用 Modal 组件
 * ============================================================================ */

// ---- 旧代码（index.vue 购物袋弹窗）----
/*
<view v-if="bagOpen" class="modal-mask" @tap="bagOpen = false">
  <view class="bag-panel" @tap.stop>
    <view class="bag-title">
      <text>购物袋</text>
      <text class="sheet-close" @tap="bagOpen = false">×</text>
    </view>
    <!-- 内容 -->
  </view>
</view>
*/

// ---- 新代码（使用 Modal 组件）----
/*
<Modal v-model:visible="bagOpen" title="购物袋">
  <!-- 内容 -->
  <template #footer>
    <Button variant="primary" @tap="checkout">去结算</Button>
  </template>
</Modal>
*/

/* ============================================================================
 * 示例 10: 使用 Card 组件
 * ============================================================================ */

// ---- 旧代码（checkout.vue）----
/*
<view class="pickup-card">
  <view class="pickup-heading">
    <text class="muted">到店自提</text>
    <text>预计 {{ readyTime }} 可取</text>
  </view>
  <StoreLine />
  <!-- 更多内容 -->
</view>
*/

// ---- 新代码（使用 Card 组件）----
/*
<Card variant="elevated">
  <view class="pickup-heading">
    <text class="muted">到店自提</text>
    <text>预计 {{ readyTime }} 可取</text>
  </view>
  <StoreLine />
  <!-- 更多内容 -->
</Card>
*/

export default {}
