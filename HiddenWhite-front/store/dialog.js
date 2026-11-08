/**
 * 全局对话框（提示 / 确认 / 输入）的唯一状态源。
 * ---------------------------------------------------------------------------
 * 为什么不再直接用 `uni.showModal`：
 *   原生 modal 的按钮、标题、圆角、字号全部由系统决定，无法引用设计令牌
 *   （$c-surface / $r-xl / $fs-title-lg …），深浅色与字阶在 iOS、Android、H5
 *   三端表现各不相同，与「编辑式奢感」的暖白纸 + 深金体系完全不搭。
 *
 * 用法：
 *   const ok = await dialogConfirm({ title: '取消订单', content: '…', danger: true })
 *   const note = await dialogPrompt({ title: '订单备注', value: shop.note })
 *   dialogNotice({ title: '后端服务地址', content: '…' })
 *
 * 渲染方：`components/ui/DialogHost.vue`，必须挂在**页面层**（不能放进
 * swiper-item —— 那里带 transform，position: fixed 会在微信端错位）。
 *
 * 单槽队列：一次只显示一个弹窗。新请求到来时先把上一个按「取消」结清，
 * 避免两个弹层叠在一起、用户点完一层发现还有一层。
 */
import { shallowRef } from 'vue'

/** 当前待渲染的对话框；null 表示没有 */
export const pendingDialog = shallowRef(null)

/** 已挂载的 DialogHost 数量。为 0 时说明当前页面没接弹窗宿主 */
let hostCount = 0

export function registerDialogHost() {
  hostCount += 1
}

/**
 * 页面卸载时调用。
 * 这里无条件结清挂起的弹窗：多页堆叠时上一页的宿主还在，若不结清，
 * 用户返回后会看到一个「上一页留下、点不掉」的弹层。
 */
export function unregisterDialogHost() {
  hostCount = Math.max(0, hostCount - 1)
  settleDialog(false)
}

/**
 * 结清当前弹窗。
 * @param {boolean} confirmed 是否点了主操作
 * @param {string|null} value prompt 模式下输入框的值
 */
export function settleDialog(confirmed, value = null) {
  const current = pendingDialog.value
  if (!current) return

  pendingDialog.value = null
  // 三种模式返回的东西本来就不同：确认返回布尔，输入返回字符串或 null
  current.resolve(current.type === 'prompt' ? (confirmed ? value : null) : confirmed)
}

/**
 * 没有宿主时的降级路径。
 * 宁可样式退回系统弹窗，也不能让调用方永远 await 不到结果
 * （例如新增页面忘了放 <DialogHost />）。
 */
function nativeDialog({ type, title, content, confirmText, cancelText, showCancel, placeholder, value }) {
  return new Promise((resolve) => {
    if (type === 'prompt') {
      uni.showModal({
        title,
        // editable 模式下 content 是输入框初始值，不是提示语
        content: value || '',
        editable: true,
        placeholderText: placeholder || content,
        confirmText,
        cancelText,
        showCancel,
        success: (res) => resolve(res.confirm ? (res.content || '').trim() : null),
        fail: () => resolve(null)
      })
      return
    }

    uni.showModal({
      title,
      content,
      confirmText,
      cancelText,
      showCancel,
      success: (res) => resolve(Boolean(res.confirm)),
      fail: () => resolve(false)
    })
  })
}

function request(options) {
  const dialog = {
    type: 'confirm',
    title: '提示',
    content: '',
    confirmText: '确定',
    cancelText: '取消',
    showCancel: true,
    danger: false,
    /**
     * 默认点遮罩不关闭：原生 modal 也只能点按钮，保持一致
     * 可以避免「确认下单」这类需要明确表态的弹窗被误触关掉。
     */
    maskClosable: false,
    ...options
  }

  if (!hostCount) return nativeDialog(dialog)

  settleDialog(false)
  return new Promise((resolve) => {
    pendingDialog.value = { ...dialog, resolve }
  })
}

/** 纯告知：只有一个主按钮，返回值恒为 true（调用方一般不关心） */
export function dialogNotice(options = {}) {
  return request({
    ...options,
    type: 'notice',
    showCancel: false,
    confirmText: options.confirmText || '知道了'
  })
}

/** 二次确认：resolve(true / false) */
export function dialogConfirm(options = {}) {
  return request({ ...options, type: 'confirm' })
}

/** 输入：resolve(去除首尾空白的字符串) 或 null（取消） */
export function dialogPrompt(options = {}) {
  return request({ ...options, type: 'prompt' })
}
