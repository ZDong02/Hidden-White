/**
 * 统一的提示消息工具
 * 封装 uni.showToast 和 uni.showModal，提供一致的 API
 */

// 成功提示
export function showSuccess(message, duration = 2000) {
  uni.showToast({
    title: message,
    icon: 'success',
    duration
  })
}

// 错误提示
export function showError(message, duration = 2000) {
  uni.showToast({
    title: message,
    icon: 'none',
    duration
  })
}

// 普通提示
export function showInfo(message, duration = 2000) {
  uni.showToast({
    title: message,
    icon: 'none',
    duration
  })
}

// 纯告知弹窗（仅「知道了」按钮，不可取消）
export function showNotice(title, content) {
  uni.showModal({
    title,
    content,
    showCancel: false,
    confirmText: '知道了'
  })
}

// 确认对话框
//
// showCancel: false 用于「只是告知 + 一个确认按钮」的场景。
// 不要用 cancelText: '' 来表达同一件事——小程序会把空字符串当成有效文案，
// 渲染出一个空白按钮，用户不知道该点哪个。
export function showConfirm(options) {
  const {
    title = '提示',
    content = '',
    confirmText = '确定',
    cancelText = '取消',
    showCancel = true,
    onConfirm,
    onCancel
  } = options

  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText,
      showCancel,
      success: (res) => {
        if (res.confirm) {
          if (onConfirm) onConfirm()
          resolve(true)
        } else {
          if (onCancel) onCancel()
          resolve(false)
        }
      }
    })
  })
}

// 输入对话框
export function showPrompt(options) {
  const {
    title = '输入',
    content = '',
    placeholder = '',
    value = '',
    onConfirm,
    onCancel
  } = options

  return new Promise((resolve) => {
    uni.showModal({
      title,
      // editable 模式下 content 为输入框初始值，只能写一次
      content: value,
      editable: true,
      // content 作为提示语使用：调用方未显式传 placeholder 时以它兜底
      placeholderText: placeholder || content,
      success: (res) => {
        if (res.confirm) {
          const inputValue = res.content?.trim() || ''
          if (onConfirm) onConfirm(inputValue)
          resolve(inputValue)
        } else {
          if (onCancel) onCancel()
          resolve(null)
        }
      }
    })
  })
}
