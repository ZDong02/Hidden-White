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

// 加载中提示
export function showLoading(message = '加载中...') {
  uni.showLoading({
    title: message,
    mask: true
  })
}

// 隐藏加载提示
export function hideLoading() {
  uni.hideLoading()
}

// 确认对话框
export function showConfirm(options) {
  const {
    title = '提示',
    content = '',
    confirmText = '确定',
    cancelText = '取消',
    onConfirm,
    onCancel
  } = options

  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText,
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

// 操作菜单
export function showActionSheet(itemList, onSelect) {
  return new Promise((resolve) => {
    uni.showActionSheet({
      itemList,
      success: (res) => {
        if (onSelect) onSelect(res.tapIndex)
        resolve(res.tapIndex)
      },
      fail: () => {
        resolve(-1)
      }
    })
  })
}
