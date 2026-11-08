/**
 * 统一的提示工具
 * ---------------------------------------------------------------------------
 * 分工：
 *   showSuccess / showError / showInfo  → 轻提示，仍是系统 toast（1.5 秒即走，
 *     不需要用户表态，做成自绘组件反而会盖住操作区、拖慢点击节奏）
 *   showNotice / showConfirm / showPrompt → 弹窗，全部走自绘 ui/Dialog，
 *     不再用 uni.showModal：原生弹窗的字体、按钮、圆角不归设计令牌管。
 *
 * 调用方的写法保持不变（同一个函数名、同一套参数、同样的返回值类型），
 * 只把渲染层换掉了。
 */
import { dialogConfirm, dialogNotice, dialogPrompt } from '../store/dialog'

/**
 * 轻提示的唯一实现。
 *
 * <p>下面的三个对外函数只是语义别名：在系统 toast 上 showError 与 showInfo
 * 完全同构（都不带图标），差别只在于「调用处想表达什么」。
 * 保留两个名字而不是合并，是因为调用点的意图比函数更值得读；
 * 但实现只留一处，避免改时长/图标时漏改其中一份。
 */
function toast(message, icon = 'none', duration = 2000) {
  uni.showToast({
    title: message,
    icon,
    duration
  })
}

// 成功提示
export function showSuccess(message, duration = 2000) {
  toast(message, 'success', duration)
}

// 错误提示
export function showError(message, duration = 2000) {
  toast(message, 'none', duration)
}

// 普通提示
export function showInfo(message, duration = 2000) {
  toast(message, 'none', duration)
}

// 纯告知弹窗（仅一个确认按钮，不可取消）
export function showNotice(title, content) {
  dialogNotice({ title, content })
}

// 确认对话框
//
// showCancel: false 用于「只是告知 + 一个确认按钮」的场景。
// 不要靠 cancelText: '' 来表达同一件事——空字符串会渲染出一个空白按钮，
// 用户不知道该点哪个。
export function showConfirm(options = {}) {
  const {
    title = '提示',
    content = '',
    confirmText = '确定',
    cancelText = '取消',
    showCancel = true,
    danger = false,
    onConfirm,
    onCancel
  } = options

  return dialogConfirm({
    title,
    content,
    confirmText,
    cancelText,
    showCancel,
    danger
  }).then((confirmed) => {
    if (confirmed) {
      if (onConfirm) onConfirm()
    } else if (onCancel) {
      onCancel()
    }
    return confirmed
  })
}

// 输入对话框
export function showPrompt(options = {}) {
  const {
    title = '输入',
    content = '',
    placeholder = '',
    value = '',
    confirmText = '确定',
    cancelText = '取消',
    maxlength = 100,
    onConfirm,
    onCancel
  } = options

  return dialogPrompt({
    title,
    content,
    placeholder,
    value,
    confirmText,
    cancelText,
    maxlength
  }).then((inputValue) => {
    if (inputValue === null) {
      if (onCancel) onCancel()
      return null
    }
    if (onConfirm) onConfirm(inputValue)
    return inputValue
  })
}
